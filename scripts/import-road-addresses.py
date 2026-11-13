"""Build a searchable address index from the prepared OpenStreetMap XML extracts."""

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path


def import_addresses(paths):
    addresses = {}
    for path in paths:
        root = ET.parse(path).getroot()
        nodes = {node.get("id"): (float(node.get("lat")), float(node.get("lon")))
                 for node in root.iterfind("node")}
        for element in root:
            if element.tag not in {"node", "way"}:
                continue
            tags = {tag.get("k"): tag.get("v", "").strip() for tag in element.iterfind("tag")}
            street = tags.get("addr:street")
            number = tags.get("addr:housenumber")
            if not street or not number:
                continue
            if element.tag == "node":
                lat, lon = nodes[element.get("id")]
                priority = 0
            else:
                refs = {ref.get("ref") for ref in element.iterfind("nd")}
                if not refs or not refs <= nodes.keys():
                    continue
                points = [nodes[ref] for ref in sorted(refs)]
                lat = sum(point[0] for point in points) / len(points)
                lon = sum(point[1] for point in points) / len(points)
                priority = 1
            label = f"{street} {number}"
            candidate = (priority, round(lat, 6), round(lon, 6))
            if label not in addresses or candidate < addresses[label]:
                addresses[label] = candidate
    return [[label, lat, lon] for label, (_, lat, lon) in sorted(addresses.items())]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("extracts", nargs="+", type=Path)
    args = parser.parse_args()
    args.output.write_text(json.dumps(import_addresses(args.extracts), ensure_ascii=False, separators=(",", ":")) + "\n",
                           encoding="utf-8")
