#!/usr/bin/env python3
"""Synthetic CodeGaze demo. No IDE or headset measurement is performed by this server."""
import argparse
import base64
import csv
import hashlib
import hmac
import io
import json
import math
import secrets
import threading
import time
import uuid
import zipfile
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = {'/':'index.html','/index.html':'index.html','/style.css':'style.css',
          '/app.mjs':'app.mjs','/core.mjs':'core.mjs','/xr.mjs':'xr.mjs'}

class State:
    def __init__(self, output):
        self.key=secrets.token_urlsafe(24)
        self.output=Path(output)
        self.frames=OrderedDict()
        self.frame_id=0
        self.lock=threading.RLock()
        self.session=None
        self.recording=False
        self.count=0
        self.last={}
        self.directory=None
        self.saved_frames=set()
        self.template=json.loads((ROOT/'demo/frame.json').read_text())
        self.image=base64.b64encode((ROOT/'demo/frame.jpg').read_bytes()).decode()
    def status(self):
        return dict(version='0.1.0',protocol=1,demo=True,recording=self.recording,samples=self.count,
                    sessionId=self.session['id'] if self.session else None,
                    directory=str(self.directory) if self.directory else None,error=None)
    def frame(self):
        self.frame_id+=1
        frame={**self.template,'id':self.frame_id,'capturedEpochMs':int(time.time()*1000),
               'capturedMonoNs':time.monotonic_ns(),'image':self.image}
        self.frames[frame['id']]=frame
        while len(self.frames)>120:self.frames.popitem(last=False)
        return frame
    def start(self, participant):
        if self.recording:raise RuntimeError('A recording is already active')
        if not isinstance(participant,str) or len(participant)>128:raise ValueError('Invalid participant label')
        self.session=dict(id=str(uuid.uuid4()),startedEpochMs=int(time.time()*1000),metadata=dict(participant=participant,demo=True,software='CodeGaze synthetic demo'))
        self.directory=self.output/(self.session['id']+'.codegaze-session')
        self.directory.mkdir(parents=True)
        (self.directory/'session.json').write_text(json.dumps(self.session),encoding='utf-8')
        (self.directory/'samples.jsonl').touch()
        (self.directory/'frames.jsonl').touch()
        with (self.directory/'samples.csv').open('w',newline='',encoding='utf-8') as f:
            csv.writer(f).writerow(['received_utc_ms','client_sequence','client_monotonic_ms','source','frame_id','mapping_status','file','revision','token','line','column'])
        self.recording=True;self.count=0;self.last={};self.saved_frames=set()
    def record(self, sample):
        if not self.recording:raise RuntimeError('Start a session before sending samples')
        client=sample.get('clientId');source=sample.get('source');sequence=sample.get('sequence')
        if not isinstance(client,str) or not 0<len(client)<=128:raise ValueError('Invalid clientId')
        if source not in ('eye','head','simulated'):raise ValueError('Invalid source')
        if not isinstance(sequence,int) or sequence<0 or sequence<=self.last.get(client,-1):raise ValueError('Duplicate or invalid sequence')
        mono=sample.get('clientMonoMs')
        if not isinstance(mono,(float,int)) or not math.isfinite(mono) or mono<0:raise ValueError('Invalid timestamp')
        u,v=sample.get('u'),sample.get('v')
        if (u is None)!=(v is None):raise ValueError('UV must be a pair')
        if u is not None and not all(isinstance(n,(float,int)) and math.isfinite(n) for n in (u,v)):raise ValueError('Invalid UV')
        frame=self.frames.get(sample.get('frameId'));target=None
        if not sample.get('valid'):status='tracking_lost'
        elif frame is None:status='frame_expired'
        elif u is None or not 0<=u<1 or not 0<=v<1:status='off_screen'
        else:
            x,y=u*frame['width'],v*frame['height']
            matches=[t for t in frame['targets'] if any(r['x']<=x<r['x']+r['width'] and r['y']<=y<r['y']+r['height'] for r in t['bounds'])]
            status='mapped' if len(matches)==1 else 'ambiguous' if len(matches)>1 else 'no_token'
            if len(matches)==1:target=matches[0]
        self.count+=1;self.last[client]=sequence
        now=int(time.time()*1000)
        event=dict(serverSequence=self.count,sessionId=self.session['id'],receivedEpochMs=now,receivedMonoNs=time.monotonic_ns(),sample=sample,mappingStatus=status,
                   frameCapturedEpochMs=frame['capturedEpochMs'] if frame else None,frameAgeMs=now-frame['capturedEpochMs'] if frame else None,target=target)
        with (self.directory/'samples.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(event)+'\n')
        with (self.directory/'samples.csv').open('a',newline='',encoding='utf-8') as f:
            t=target or {}
            csv.writer(f).writerow([now,sequence,mono,source,sample.get('frameId'),status,t.get('file'),t.get('revision'),t.get('text'),t.get('line'),t.get('column')])
        if frame and frame['id'] not in self.saved_frames:
            self.saved_frames.add(frame['id'])
            with (self.directory/'frames.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps({**frame,'image':None})+'\n')
        return event
    def export(self):
        if not self.directory:raise RuntimeError('No session to export')
        if self.recording:raise RuntimeError('Stop recording before exporting')
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
            for name in ('session.json','samples.jsonl','samples.csv','frames.jsonl'):z.write(self.directory/name,name)
        return out.getvalue()

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):self.dispatch()
    def do_POST(self):self.dispatch()
    def send(self,code,body,content_type='application/json'):
        data=json.dumps(body).encode() if isinstance(body,(dict,list)) else body
        self.send_response(code);self.send_header('Content-Type',content_type);self.send_header('Content-Length',str(len(data)))
        self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(data)
    def dispatch(self):
        state=self.server.state
        try:
            host=self.headers.get('Host','');allowed={f'localhost:{self.server.server_port}',f'127.0.0.1:{self.server.server_port}'}
            if host not in allowed:return self.send(403,{'error':'Invalid Host'})
            origin=self.headers.get('Origin')
            if origin and origin not in {'http://'+h for h in allowed}:return self.send(403,{'error':'Cross-origin access disabled'})
            path=self.path.split('?')[0]
            if not path.startswith('/api/'):
                if path not in ASSETS:return self.send(404,{'error':'Not found'})
                name=ASSETS[path];kind='text/html' if name.endswith('.html') else 'text/css' if name.endswith('.css') else 'text/javascript'
                return self.send(200,(ROOT/'web'/name).read_bytes(),kind)
            if not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+state.key):return self.send(401,{'error':'Enter the pairing key printed by the demo server'})
            data={}
            if self.command=='POST':
                length=int(self.headers.get('Content-Length','0'))
                if not 0<=length<=262144:raise ValueError('Request too large')
                data=json.loads(self.rfile.read(length))
                if not isinstance(data,dict):raise ValueError('Expected JSON object')
            with state.lock:
                if path=='/api/status' and self.command=='GET':return self.send(200,state.status())
                if path=='/api/frame' and self.command=='GET':return self.send(200,state.frame())
                if path=='/api/session/start' and self.command=='POST':state.start(data.get('participant','anonymous'));return self.send(200,state.status())
                if path=='/api/session/stop' and self.command=='POST':state.recording=False;return self.send(200,state.status())
                if path=='/api/samples' and self.command=='POST':
                    samples=data.get('samples')
                    if not isinstance(samples,list) or len(samples)>256:raise ValueError('Expected up to 256 samples')
                    events=[state.record(s) for s in samples]
                    return self.send(200,dict(accepted=len(events),last=events[-1] if events else None))
                if path=='/api/export' and self.command=='GET':return self.send(200,state.export(),'application/zip')
                return self.send(404,{'error':'Unknown endpoint or method'})
        except (ValueError,KeyError,TypeError) as e:self.send(400,{'error':str(e)})
        except RuntimeError as e:self.send(409,{'error':str(e)})

def create_server(port=8742,output=ROOT/'recordings'):
    server=ThreadingHTTPServer(('127.0.0.1',port),Handler)
    server.state=State(output)
    return server

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--port',type=int,default=8742);args=parser.parse_args()
    server=create_server(args.port)
    print('Synthetic demo only. No IDE or eye tracker is connected.',flush=True)
    print(f'Open http://127.0.0.1:{server.server_port}/#key={server.state.key}',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()
if __name__=='__main__':main()
