"""Generate the offline maneuver metadata migration from the original OSM extracts."""

import argparse
from collections import defaultdict
import json
from pathlib import Path
import sqlite3
import xml.etree.ElementTree as ET


def tags(element):
    return {t.get('k'): t.get('v') for t in element.findall('tag')}


def restricted_sequences(relation, by_way, outgoing):
    metadata = tags(relation)
    value = next((metadata[key] for key in ('restriction:motorcar', 'restriction:motor_vehicle',
                                             'restriction:vehicle', 'restriction') if key in metadata), '')
    exemptions = set(metadata.get('except', '').split(';'))
    if exemptions & {'motorcar', 'motor_vehicle', 'vehicle'} or not value.startswith(('no_', 'only_')):
        return set()

    members = relation.findall('member')
    from_ways = [m.get('ref') for m in members if m.get('role') == 'from' and m.get('type') == 'way']
    to_ways = [m.get('ref') for m in members if m.get('role') == 'to' and m.get('type') == 'way']
    via = [m for m in members if m.get('role') == 'via']
    if not via or not to_ways:
        return set()
    starts = [edge for way in from_ways for edge in by_way[way]]
    ends = defaultdict(list)
    for way in to_ways:
        for edge in by_way[way]:
            ends[edge[1]].append(edge)

    def match_via(way_index, node, path, seen):
        if way_index == len(via):
            for edge in ends[node]:
                yield path + (edge[0],)
            return
        way_edges = by_way[via[way_index].get('ref')]
        for eid, start, end in way_edges:
            if start != node or end in seen:
                continue
            expanded = path + (eid,)
            yield from match_via(way_index + 1, end, expanded, {end})
            yield from match_via(way_index, end, expanded, seen | {end})

    candidates = set()
    if len(via) == 1 and via[0].get('type') == 'node':
        node = via[0].get('ref')
        for first in starts:
            if first[2] != node:
                continue
            for last in ends[node]:
                if value.endswith('u_turn') and from_ways == to_ways and first[1] != last[2]:
                    continue
                if value.endswith('straight_on') and from_ways == to_ways and first[1] == last[2]:
                    continue
                candidates.add((first[0], last[0]))
    elif all(m.get('type') == 'way' for m in via):
        first_nodes = {edge[1] for edge in by_way[via[0].get('ref')]}
        for eid, _, end in starts:
            if end in first_nodes:
                candidates.update(match_via(0, end, (eid,), {end}))
    if value.startswith('no_'):
        return candidates

    allowed = defaultdict(set)
    for path in candidates:
        for i in range(1, len(path)):
            allowed[path[:i]].add(path[i])
    nodes_by_edge = {edge[0]: edge[2] for edges in outgoing.values() for edge in edges}
    return {prefix + (edge[0],) for prefix, next_ids in allowed.items()
            for edge in outgoing[nodes_by_edge[prefix[-1]]] if edge[0] not in next_ids}


def generate(db, objects):
    circle_ways = {key[1] for key, element in objects.items() if key[0] == 'way'
                   and tags(element).get('junction') in {'roundabout', 'circular'}}
    relations = [element for key, element in sorted(objects.items())
                 if key[0] == 'relation' and tags(element).get('type', '').startswith('restriction')]
    yield '''-- © OpenStreetMap contributors, ODbL 1.0. https://www.openstreetmap.org/copyright
CREATE TABLE road_turn_rule (
    network_id TEXT NOT NULL REFERENCES road_network(id),
    edge_sequence TEXT NOT NULL,
    PRIMARY KEY (network_id, edge_sequence)
);
CREATE TABLE road_roundabout (
    network_id TEXT NOT NULL,
    edge_id TEXT NOT NULL,
    group_id TEXT NOT NULL,
    PRIMARY KEY (network_id, edge_id),
    FOREIGN KEY (network_id, edge_id) REFERENCES road_edge(network_id, edge_id)
);'''
    for network, in db.execute("SELECT id FROM road_network WHERE id LIKE 'gdansk-%' ORDER BY id"):
        by_way, outgoing = defaultdict(list), defaultdict(list)
        roundabout_edges, parent = [], {}

        def root(node):
            parent.setdefault(node, node)
            while node != parent[node]:
                node = parent[node]
            return node

        for edge in db.execute('SELECT edge_id, from_node_id, to_node_id FROM road_edge WHERE network_id=? ORDER BY edge_id',
                               (network,)):
            way = edge[0].split('-')[0]
            by_way[way].append(edge)
            outgoing[edge[1]].append(edge)
            if way in circle_ways:
                first, second = sorted((root(edge[1]), root(edge[2])))
                parent[second] = first
                roundabout_edges.append(edge)

        forbidden = set()
        for relation in relations:
            forbidden.update(restricted_sequences(relation, by_way, outgoing))
        for path in sorted(forbidden):
            yield f"INSERT INTO road_turn_rule VALUES ('{network}','{json.dumps(path, separators=(',', ':'))}');"
        for eid, start, _ in roundabout_edges:
            yield f"INSERT INTO road_roundabout VALUES ('{network}','{eid}','{root(start)}');"


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('extracts', nargs='+')
    args = parser.parse_args()
    objects = {}
    for filename in args.extracts:
        for _, element in ET.iterparse(filename, events=('end',)):
            if element.tag not in {'relation', 'way'}:
                continue
            key = (element.tag, element.get('id'))
            if key not in objects or int(element.get('version', 0)) > int(objects[key].get('version', 0)):
                objects[key] = element
    db = sqlite3.connect(':memory:')
    migrations = Path(__file__).resolve().parents[1] / 'backend/src/main/resources/db/migration'
    for file in sorted(migrations.glob('V[1-5]__*.sql')):
        db.executescript(file.read_text())
    print('\n'.join(generate(db, objects)))
