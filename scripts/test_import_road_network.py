"""Test the offline importer without downloading map data."""

from pathlib import Path
import runpy
import unittest
import xml.etree.ElementTree as ET

convert = runpy.run_path(str(Path(__file__).with_name('import-road-network.py')))['convert']


class RoadImportTest(unittest.TestCase):
    def fixture(self, direction, end='2'):
        return ET.fromstring(f"""
            <osm><node id="1" lat="54.4" lon="18.58"/>
            <node id="2" lat="54.401" lon="18.58"/>
            <way id="5"><nd ref="1"/><nd ref="{end}"/>
            <tag k="highway" v="primary"/><tag k="oneway" v="{direction}"/>
            </way></osm>
        """)

    def test_reverse_one_way(self):
        sql = '\n'.join(convert(self.fixture('-1')))
        self.assertIn("'5-0-r','2','1'", sql)
        self.assertNotIn("'5-0-f'", sql)

    def test_missing_node(self):
        with self.assertRaisesRegex(ValueError, 'Missing node'):
            list(convert(self.fixture('yes', '3')))


if __name__ == '__main__':
    unittest.main()
