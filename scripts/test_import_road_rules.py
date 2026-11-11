"""Check translation from OSM restriction members to directed edge sequences."""

from collections import defaultdict
from pathlib import Path
import runpy
import unittest
import xml.etree.ElementTree as ET

restricted_sequences = runpy.run_path(str(Path(__file__).with_name('import-road-rules.py')))['restricted_sequences']


class RoadRuleImportTest(unittest.TestCase):
    def setUp(self):
        self.by_way = defaultdict(list, {
            '1': [('approach', 'a', 'b')],
            '2': [('connector', 'b', 'c')],
            '3': [('straight', 'c', 'd')],
            '4': [('turn', 'b', 'x'), ('exit', 'c', 'y')],
        })
        self.outgoing = defaultdict(list)
        for edges in self.by_way.values():
            for edge in edges:
                self.outgoing[edge[1]].append(edge)

    def test_only_via_way_bans_exits_before_and_after_the_connector(self):
        rule = ET.fromstring('''<relation>
            <member type="way" ref="1" role="from"/>
            <member type="way" ref="2" role="via"/>
            <member type="way" ref="3" role="to"/>
            <tag k="restriction" v="only_straight_on"/>
        </relation>''')
        self.assertEqual({('approach', 'turn'), ('approach', 'connector', 'exit')},
                         restricted_sequences(rule, self.by_way, self.outgoing))

    def test_car_exemption_and_no_left_turn(self):
        rule = ET.fromstring('''<relation>
            <member type="way" ref="1" role="from"/>
            <member type="node" ref="b" role="via"/>
            <member type="way" ref="4" role="to"/>
            <tag k="restriction" v="no_left_turn"/>
        </relation>''')
        self.assertEqual({('approach', 'turn')}, restricted_sequences(rule, self.by_way, self.outgoing))
        ET.SubElement(rule, 'tag', {'k': 'except', 'v': 'motorcar'})
        self.assertFalse(restricted_sequences(rule, self.by_way, self.outgoing))


if __name__ == '__main__':
    unittest.main()
