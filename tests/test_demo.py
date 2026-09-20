import io
import json
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
import zipfile
from tools.demo_server import create_server

class DemoTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.server=create_server(0,self.temp.name)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.base=f'http://127.0.0.1:{self.server.server_port}'
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def request(self,path,body=None,auth=True,origin=None):
        headers={'Authorization':'Bearer '+self.server.state.key} if auth else {}
        if origin:headers['Origin']=origin
        req=urllib.request.Request(self.base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
        with urllib.request.urlopen(req) as response:return response.read()
    def test_auth_and_origin_checks(self):
        for kwargs,code in [({'auth':False},401),({'origin':'https://example.com'},403)]:
            with self.assertRaises(urllib.error.HTTPError) as raised:self.request('/api/status',**kwargs)
            self.assertEqual(raised.exception.code,code)
    def test_live_frame_record_export(self):
        frame=json.loads(self.request('/api/frame'))
        token=next(t for t in frame['targets'] if t['text']=='quantity')
        r=token['bounds'][0]
        self.request('/api/session/start',{'participant':'P01'})
        sample=dict(clientId='test',sequence=0,frameId=frame['id'],clientMonoMs=12,clientEpochMs=1000,source='simulated',fallbackReason='test',valid=True,u=(r['x']+r['width']/2)/frame['width'],v=(r['y']+r['height']/2)/frame['height'],origin=None,direction=None,sensorTime=None,sensorTimeBasis='unavailable')
        result=json.loads(self.request('/api/samples',{'samples':[sample]}))
        self.assertEqual(result['last']['target']['text'],'quantity')
        with self.assertRaises(urllib.error.HTTPError):self.request('/api/samples',{'samples':[sample]})
        self.request('/api/session/stop',{})
        with zipfile.ZipFile(io.BytesIO(self.request('/api/export'))) as archive:
            self.assertEqual(len(archive.namelist()),4)
            self.assertEqual(json.loads(archive.read('samples.jsonl'))['sample']['source'],'simulated')
    def test_stale_frame_never_uses_latest(self):
        self.request('/api/session/start',{})
        sample=dict(clientId='test',sequence=0,frameId=99999,clientMonoMs=12,source='head',valid=True,u=.5,v=.5)
        result=json.loads(self.request('/api/samples',{'samples':[sample]}))
        self.assertEqual(result['last']['mappingStatus'],'frame_expired')
        self.assertIsNone(result['last']['target'])

if __name__=='__main__':unittest.main()
