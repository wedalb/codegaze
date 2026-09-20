import {findTarget} from './core.mjs';
import {XRMonitor} from './xr.mjs';
const $=id=>document.getElementById(id);
const clientId=crypto.randomUUID();
let key=new URLSearchParams(location.hash.slice(1)).get('key')||'',connected=false,recording=false,simulating=false,frame=null,sequence=0,pointer=null,pending=[],sending=false,stopping=false,frameBusy=false,activeSessionId=null;
history.replaceState(null,'',location.pathname);
$('key').value=key;
const xr=new XRMonitor(sample=>queueSample(sample),()=>{setSource();$('vr').textContent='Enter VR · head direction';});
function message(text){$('message').textContent=text;$('message').hidden=!text;}
async function api(path,body){
  const response=await fetch('/api/'+path,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+key,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});
  if(!response.ok){let info;try{info=await response.json();}catch{}throw new Error(info?.error||'Request failed ('+response.status+')');}
  return path==='export'?response.blob():response.json();
}
async function connect(){
  key=$('key').value.trim();
  try{applyStatus(await api('status'));connected=true;$('pairing').hidden=true;$('connection').textContent='Connected to IntelliJ';$('record').disabled=false;$('simulate').disabled=false;message('');await checkXR();await refreshFrame();}
  catch(e){message(e.message);connected=false;$('pairing').hidden=false;$('connection').textContent='Connection failed';}
}
async function checkXR(){
  try{const supported=!!navigator.xr&&await navigator.xr.isSessionSupported('immersive-vr');$('vr').disabled=!supported;if(!supported)$('vr-help').textContent='No WebXR headset detected in this browser. Use the native OpenXR client for PC VR, or open this page in your headset browser. Desktop simulation works here.';}
  catch{$('vr').disabled=true;}
}
function applyStatus(status){
  if(activeSessionId!==status.sessionId){pending=[];activeSessionId=status.sessionId;}
  recording=status.recording;$('count').textContent=status.samples.toLocaleString();$('record-label').textContent=recording?'Recording':'Not recording';$('record-dot').classList.toggle('active',recording);$('record').textContent=recording?'Stop recording':'Start recording';$('export').disabled=recording||!status.sessionId;
  if(status.demo){$('connection').textContent='Demo server · synthetic code';document.querySelector('.version').textContent='Demonstration · synthetic data';}
  if(status.error)message(status.error);
}
async function refreshFrame(){
  if(!connected||frameBusy)return;frameBusy=true;
  try{
    const next=await api('frame');
    if(next.id===frame?.id)return;
    const image=new Image();image.src='data:image/jpeg;base64,'+next.image;await image.decode();
    frame=next;$('editor').src=image.src;$('editor').hidden=false;$('empty').hidden=true;$('file').textContent=frame.title;$('frame-info').textContent='Frame '+frame.id+' · '+frame.width+' × '+frame.height;
    xr.setFrame(frame,image);
    if(frame.status!=='ready')message('Open a source file in IntelliJ to begin.');
  }catch(e){message(e.message);}
  finally{frameBusy=false;}
}
function setSource(){
  $('source').textContent=xr.session?'Head direction':simulating?'Mouse simulator':'Preview only';
  $('source-detail').textContent=xr.session?'Centre of the headset view':simulating?'Synthetic pointing data; not eye tracking':'Use the native client for eye gaze.';
  $('simulate').textContent=simulating?'Disable mouse simulator':'Enable mouse simulator';
}
function queueSample(sample){
  const observed=sample.frameId===frame?.id?findTarget(frame,sample.u,sample.v):null;
  $('target').textContent=observed?observed.text:sample.valid?'Outside a code token':'Tracking unavailable';
  $('location').textContent=observed?'Line '+observed.line+', column '+observed.column:'—';
  $('reticle').hidden=!$('show-reticle').checked||sample.u===null||!sample.valid;
  if(!$('reticle').hidden){$('reticle').style.left=(sample.u*100)+'%';$('reticle').style.top=(sample.v*100)+'%';}
  if(!recording||stopping)return;
  pending.push({...sample,clientId,sequence:sequence++,clientEpochMs:Date.now(),sensorTime:null,sensorTimeBasis:'unavailable; clientMonoMs is software sampling time'});
  if(pending.length>300){pending=[];recording=false;message('Recording interrupted: the server is not keeping up. Stop the session and inspect the saved data.');}
}
async function flush(){
  if(sending||!pending.length)return;sending=true;const batch=pending.splice(0,120);
  try{const result=await api('samples',{sessionId:activeSessionId,samples:batch});if(result.accepted!==batch.length)throw new Error('The server did not accept every sample.');}
  catch(e){message('Sample delivery failed: '+e.message+'. No automatic retry, to avoid duplicate measurements.');}
  finally{sending=false;}
}
$('connect').onclick=connect;
$('key').onkeydown=e=>{if(e.key==='Enter')connect();};
$('simulate').onclick=()=>{simulating=!simulating;setSource();};
$('editor').onpointermove=e=>{const rect=$('editor').getBoundingClientRect();pointer={u:(e.clientX-rect.left)/rect.width,v:(e.clientY-rect.top)/rect.height};};
$('editor').onpointerleave=()=>{pointer=null;$('reticle').hidden=true;};
$('show-reticle').onchange=()=>{if(!$('show-reticle').checked)$('reticle').hidden=true;};
$('record').onclick=async()=>{
  $('record').disabled=true;
  try{
    if(recording){stopping=true;while(sending)await new Promise(r=>setTimeout(r,20));while(pending.length)await flush();applyStatus(await api('session/stop',{}));stopping=false;}
    else{pending=[];applyStatus(await api('session/start',{participant:$('participant').value}));}
    message('');
  }catch(e){message(e.message);stopping=false;}
  finally{$('record').disabled=!connected;}
};
$('export').onclick=async()=>{try{const blob=await api('export'),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='codegaze-session.zip';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){message(e.message);}};
$('vr').onclick=async()=>{try{if(xr.session){await xr.session.end();return;}await xr.start();simulating=false;setSource();$('vr').textContent='Exit VR';message('Use your controller trigger to reposition the virtual screen.');}catch(e){message(e.message);}};
setInterval(()=>{if(!xr.session&&simulating&&frame)queueSample({frameId:frame.id,source:'simulated',fallbackReason:'mouse_simulator',valid:!!pointer,u:pointer?.u??null,v:pointer?.v??null,origin:null,direction:null,clientMonoMs:performance.now()});},34);
setInterval(refreshFrame,220);setInterval(flush,100);
setInterval(async()=>{if(connected&&!stopping){try{applyStatus(await api('status'));}catch(e){message(e.message);}}},1000);
window.addEventListener('beforeunload',e=>{if(recording){e.preventDefault();e.returnValue='Recording is active';}});
setSource();if(key)connect();else checkXR();
