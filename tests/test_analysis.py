import unittest
from tools.analyze import derive

class DwellTests(unittest.TestCase):
    def event(self, sequence, timestamp, source='eye', revision='v1', valid=True):
        return dict(sessionId='s',mappingStatus='mapped' if valid else 'tracking_lost',
            sample=dict(clientId='c',sequence=sequence,clientMonoMs=timestamp,source=source,valid=valid),
            target=dict(file='Example.java',revision=revision,text='value',startOffset=10,endOffset=15,line=1,column=11) if valid else None)
    def test_contiguous_same_token_becomes_dwell(self):
        rows=derive([self.event(i,i*40) for i in range(5)])
        self.assertEqual(len(rows),1);self.assertEqual(rows[0]['duration_ms'],160);self.assertEqual(rows[0]['samples'],5)
    def test_sources_do_not_mix(self):
        rows=derive([self.event(i,i*40,'eye' if i<4 else 'head') for i in range(8)])
        self.assertEqual([r['source'] for r in rows],['eye','head'])
    def test_gap_invalid_and_document_change_split(self):
        for change in ('gap','invalid','revision'):
            events=[self.event(i,i*40) for i in range(4)]
            if change=='gap':events += [self.event(i,1000+i*40) for i in range(4,8)]
            elif change=='invalid':events += [self.event(4,160,valid=False)]+[self.event(i,i*40) for i in range(5,9)]
            else:events += [self.event(i,i*40,revision='v2') for i in range(4,8)]
            self.assertEqual(len(derive(events)),2,change)
    def test_missing_sequence_breaks_interval(self):
        self.assertEqual(derive([self.event(0,0),self.event(2,40),self.event(3,80)]),[])

if __name__=='__main__':unittest.main()
