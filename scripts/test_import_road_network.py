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

    def test_v2_parking_access_and_barriers(self):
        fixture = self.fixture('no')
        fixture.extend(ET.fromstring("""
            <osm>
              <node id="3" lat="54.402" lon="18.58"/>
              <node id="4" lat="54.403" lon="18.58"><tag k="barrier" v="gate"/></node>
              <way id="6"><nd ref="2"/><nd ref="3"/>
                <tag k="highway" v="service"/><tag k="service" v="parking_aisle"/>
                <tag k="oneway" v="yes"/></way>
              <way id="7"><nd ref="1"/><nd ref="3"/>
                <tag k="highway" v="service"/><tag k="access" v="private"/></way>
              <way id="8"><nd ref="3"/><nd ref="4"/><tag k="highway" v="service"/></way>
              <way id="9"><nd ref="1"/><nd ref="3"/>
                <tag k="highway" v="service"/><tag k="service" v="driveway"/>
                <tag k="access" v="permissive"/></way>
            </osm>
        """))
        sql = '\n'.join(convert(fixture, 'v2'))
        self.assertIn("'6-0-f','2','3'", sql)
        self.assertNotIn("'6-0-r'", sql)
        self.assertNotIn("'7-0-f'", sql)
        self.assertNotIn("'8-0-f'", sql)
        self.assertIn("'9-0-f'", sql)
        self.assertNotIn('ALTER TABLE', sql)


if __name__ == '__main__':
    unittest.main()
