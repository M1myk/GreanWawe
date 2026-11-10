"""Convert an OSM XML extract to a versioned Gdansk road migration."""

import argparse
import math
import re
import xml.etree.ElementTree as ET

BOUNDS = (54.383, 18.559, 54.426, 18.635)
ROAD_TYPES = {
    'primary', 'secondary', 'tertiary', 'primary_link', 'secondary_link',
    'tertiary_link', 'residential', 'unclassified',
}


ACCESS_KEYS = ('motorcar', 'motor_vehicle', 'vehicle', 'access')
OPEN_ACCESS = {'yes', 'permissive', 'designated'}


def car_access(tags):
    return next((tags[key] for key in ACCESS_KEYS if key in tags), '')


def conditional_access(tags):
    return any(key + ':conditional' in tags for key in ACCESS_KEYS)


def tags_of(element):
    return {tag.attrib['k']: tag.attrib['v'] for tag in element.findall('tag')}


def distance(a, b):
    lat1, lat2 = map(math.radians, (a[0], b[0]))
    dlon = math.radians(b[1] - a[1])
    h = math.sin((lat2 - lat1) / 2) ** 2
    h += math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 12_742_000 * math.asin(math.sqrt(min(1, h)))


def rows(table, values):
    for offset in range(0, len(values), 200):
        yield 'INSERT INTO ' + table + ' VALUES\n' + ',\n'.join(values[offset:offset + 200]) + ';'


def convert(elements, version='v1'):
    network = 'gdansk-' + version
    expanded = version != 'v1'
    south, west, north, east = BOUNDS
    if version == 'v3':
        east = 18.652
    road_types = ROAD_TYPES | {'service', 'living_street'} if expanded else ROAD_TYPES
    blocked_nodes = set()
    if expanded:
        for node in (item for item in elements if item.tag == 'node'):
            tags = tags_of(node)
            access = car_access(tags)
            barrier = tags.get('barrier')
            if conditional_access(tags) or (access and access not in OPEN_ACCESS) or (
                barrier and barrier not in {'no', 'cattle_grid', 'toll_booth', 'border_control'}
                and access not in OPEN_ACCESS
            ):
                blocked_nodes.add(int(node.attrib['id']))
    coordinates = {int(node.attrib['id']): (float(node.attrib['lat']), float(node.attrib['lon']))
                   for node in (item for item in elements if item.tag == 'node')}
    used = set()
    edges = []
    for way in (item for item in elements if item.tag == 'way'):
        tags = tags_of(way)
        access = car_access(tags)
        road_type = tags.get('highway')
        if road_type not in road_types or access in {'no', 'private'}:
            continue
        if expanded:
            if conditional_access(tags) or (access and access not in OPEN_ACCESS):
                continue
            if tags.get('area') == 'yes' or tags.get('service') in {'emergency_access', 'slipway'}:
                continue
            if tags.get('service') == 'driveway' and access not in OPEN_ACCESS:
                continue
        ids = [int(node.attrib['ref']) for node in way.findall('nd')]
        speed = tags.get('maxspeed', '')
        default_speed = {'service': 15, 'living_street': 20}.get(road_type, 50)
        speed = int(speed) if re.fullmatch(r'[1-9][0-9]{0,2}', speed) else default_speed
        direction = tags.get('oneway', 'yes' if tags.get('junction') == 'roundabout' else 'no')
        if direction in {'reversible', 'alternating'}:
            continue
        for index, (a, b) in enumerate(zip(ids, ids[1:])):
            if a not in coordinates or b not in coordinates:
                raise ValueError(f"Missing node in way {way.attrib['id']}")
            if not all(south <= coordinates[node][0] <= north
                       and west <= coordinates[node][1] <= east for node in (a, b)):
                continue
            if a in blocked_nodes or b in blocked_nodes:
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
                edges.append(f"('{network}','{int(way.attrib['id'])}-{index}-{suffix}','{start}','{end}',{length:.6f},{speed / 3.6:.6f},'{name}')")
    if not edges:
        raise ValueError('No eligible road edges')
    yield '-- © OpenStreetMap contributors, ODbL 1.0. https://www.openstreetmap.org/copyright'
    yield f"INSERT INTO road_network VALUES ('{network}','{version}','Gdańsk road extract');"
    yield from rows('road_node', [f"('{network}','{node}',{coordinates[node][0]},{coordinates[node][1]})"
                                  for node in sorted(used)])
    if not expanded:
        yield "ALTER TABLE road_edge ADD COLUMN name TEXT NOT NULL DEFAULT '';"
    yield from rows('road_edge', edges)
    yield f"INSERT INTO scenario VALUES ('{network}','{version}','{network}','Gdańsk road extract');"


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', choices=('v1', 'v2', 'v3'), default='v1')
    parser.add_argument('extracts', nargs='+')
    args = parser.parse_args()
    elements = {}
    for filename in args.extracts:
        for element in ET.parse(filename).getroot():
            if element.tag not in {'node', 'way'}:
                continue
            key = element.tag, int(element.attrib['id'])
            previous = elements.get(key)
            if previous is None or int(element.get('version', 0)) > int(previous.get('version', 0)):
                elements[key] = element
    migration = list(convert([elements[key] for key in sorted(elements)], args.version))
    print('\n'.join(migration))
