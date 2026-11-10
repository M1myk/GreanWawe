"""Convert an OSM map XML extract to the immutable Gdansk v1 SQL migration."""

import math
import re
import sys
import xml.etree.ElementTree as ET

NETWORK = 'gdansk-v1'
BOUNDS = (54.383, 18.559, 54.426, 18.635)
ROAD_TYPES = {
    'primary', 'secondary', 'tertiary', 'primary_link', 'secondary_link',
    'tertiary_link', 'residential', 'unclassified',
}


def distance(a, b):
    lat1, lat2 = map(math.radians, (a[0], b[0]))
    dlon = math.radians(b[1] - a[1])
    h = math.sin((lat2 - lat1) / 2) ** 2
    h += math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 12_742_000 * math.asin(math.sqrt(min(1, h)))


def rows(table, values):
    for offset in range(0, len(values), 200):
        yield 'INSERT INTO ' + table + ' VALUES\n' + ',\n'.join(values[offset:offset + 200]) + ';'


def convert(elements):
    coordinates = {int(node.attrib['id']): (float(node.attrib['lat']), float(node.attrib['lon']))
                   for node in (item for item in elements if item.tag == 'node')}
    used = set()
    edges = []
    for way in (item for item in elements if item.tag == 'way'):
        tags = {tag.attrib['k']: tag.attrib['v'] for tag in way.findall('tag')}
        access = next((tags[key] for key in ('motorcar', 'motor_vehicle', 'vehicle', 'access')
                       if key in tags), '')
        if tags.get('highway') not in ROAD_TYPES or access in {'no', 'private'}:
            continue
        ids = [int(node.attrib['ref']) for node in way.findall('nd')]
        speed = tags.get('maxspeed', '')
        speed = int(speed) if re.fullmatch(r'[1-9][0-9]{0,2}', speed) else 50
        direction = tags.get('oneway', 'yes' if tags.get('junction') == 'roundabout' else 'no')
        if direction in {'reversible', 'alternating'}:
            continue
        for index, (a, b) in enumerate(zip(ids, ids[1:])):
            if a not in coordinates or b not in coordinates:
                raise ValueError(f"Missing node in way {way.attrib['id']}")
            if not all(BOUNDS[0] <= coordinates[node][0] <= BOUNDS[2]
                       and BOUNDS[1] <= coordinates[node][1] <= BOUNDS[3] for node in (a, b)):
                continue
            length = distance(coordinates[a], coordinates[b])
            if length < 0.001:
                continue
            used.update((a, b))
            directions = []
            if direction != '-1':
                directions.append((a, b, 'f'))
            if direction not in {'yes', '1', 'true'}:
                directions.append((b, a, 'r'))
            name = tags.get('name', '').replace("'", "''")
            for start, end, suffix in directions:
                edges.append(f"('{NETWORK}','{int(way.attrib['id'])}-{index}-{suffix}','{start}','{end}',{length:.6f},{speed / 3.6:.6f},'{name}')")
    if not edges:
        raise ValueError('No eligible road edges')
    yield '-- © OpenStreetMap contributors, ODbL 1.0. https://www.openstreetmap.org/copyright'
    yield f"INSERT INTO road_network VALUES ('{NETWORK}','v1','Gdańsk road extract');"
    yield from rows('road_node', [f"('{NETWORK}','{node}',{coordinates[node][0]},{coordinates[node][1]})"
                                  for node in sorted(used)])
    yield "ALTER TABLE road_edge ADD COLUMN name TEXT NOT NULL DEFAULT '';"
    yield from rows('road_edge', edges)
    yield f"INSERT INTO scenario VALUES ('{NETWORK}','v1','{NETWORK}','Gdańsk road extract');"


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit('Usage: python3 scripts/import-road-network.py extract.osm [more.osm ...]')
    elements = {}
    for filename in sys.argv[1:]:
        for element in ET.parse(filename).getroot():
            if element.tag not in {'node', 'way'}:
                continue
            key = element.tag, int(element.attrib['id'])
            previous = elements.get(key)
            if previous is None or int(element.get('version', 0)) > int(previous.get('version', 0)):
                elements[key] = element
    migration = list(convert([elements[key] for key in sorted(elements)]))
    print('\n'.join(migration))
