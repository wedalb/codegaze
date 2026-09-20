#!/usr/bin/env python3
"""Run the actual Godot client against the synthetic HTTP service, without a headset."""
import argparse
import subprocess
import tempfile
import threading
from pathlib import Path
from demo_server import create_server

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--godot',default='godot')
args=parser.parse_args()
with tempfile.TemporaryDirectory() as output:
    server=create_server(0,output)
    thread=threading.Thread(target=server.serve_forever,daemon=True)
    thread.start()
    try:
        result=subprocess.run([args.godot,'--headless','--xr-mode','off','--path',str(Path(__file__).resolve().parents[1]/'native'),
            '--script','test_client.gd','--',f'--server=http://127.0.0.1:{server.server_port}',f'--key={server.state.key}'],
            timeout=30,capture_output=True,text=True)
        print(result.stdout)
        if result.stderr:print(result.stderr)
        if result.returncode or 'PASS: native authenticated connection' not in result.stdout:
            raise SystemExit(result.returncode or 1)
    finally:
        server.shutdown();server.server_close();thread.join()
