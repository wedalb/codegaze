import {makePanel,intersectPanel,panelMatrix,rotate} from './core.mjs';

/** Minimal dependency-free WebXR monitor. WebXR does not expose eye gaze here. */
export class XRMonitor {
  constructor(onSample,onEnd) { this.onSample=onSample;this.onEnd=onEnd;this.session=null;this.pending=null;this.current=null;this.lastSample=0; }
  setFrame(frame,image) { this.pending={frame,image}; }
  async start() {
    if (!navigator.xr) throw new Error('WebXR is unavailable. Use a headset browser on localhost/HTTPS, or the native OpenXR client.');
    const canvas=document.createElement('canvas');
    this.gl=canvas.getContext('webgl',{xrCompatible:true,alpha:false});
    if(!this.gl)throw new Error('WebGL is unavailable in this browser.');
    const gl=this.gl; await gl.makeXRCompatible();
    this.program=createProgram(gl);this.buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
    gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-.5,-.5,0,0, .5,-.5,1,0, -.5,.5,0,1, .5,.5,1,1]),gl.STATIC_DRAW);
    this.texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,this.texture);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([25,37,54,255]));
    const session=await navigator.xr.requestSession('immersive-vr',{optionalFeatures:['local-floor']});
    this.session=session;this.panel=null;this.current=null;this.lastSample=0;
    session.updateRenderState({baseLayer:new XRWebGLLayer(session,gl)});
    this.reference=await session.requestReferenceSpace('local');
    session.addEventListener('end',()=>{this.session=null;gl.deleteTexture(this.texture);gl.deleteBuffer(this.buffer);gl.deleteProgram(this.program);this.onEnd();});
    session.addEventListener('select',()=>{this.panel=null;});
    this.reference.addEventListener('reset',()=>{this.panel=null;});
    session.requestAnimationFrame((time,frame)=>this.render(time,frame));
  }
  render(time,frame) {
    if(!this.session)return;
    this.session.requestAnimationFrame((t,f)=>this.render(t,f));
    const pose=frame.getViewerPose(this.reference), gl=this.gl;
    if(!pose || this.session.visibilityState!=='visible') {
      if(time-this.lastSample>=33 && this.current){this.lastSample=time;this.onSample({frameId:this.current.id,source:'head',fallbackReason:'webxr_head_only',valid:false,u:null,v:null,origin:null,direction:null,clientMonoMs:time});}
      return;
    }
    const p=pose.transform.position,origin=[p.x,p.y,p.z],orientation=pose.transform.orientation;
    if(this.pending){gl.bindTexture(gl.TEXTURE_2D,this.texture);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,this.pending.image);this.current=this.pending.frame;this.pending=null;}
    if(!this.panel)this.panel=makePanel(origin,orientation,1.8,this.current?this.current.width/this.current.height:16/9);
    if(this.current)this.panel.height=this.panel.width/(this.current.width/this.current.height);
    const layer=this.session.renderState.baseLayer;gl.bindFramebuffer(gl.FRAMEBUFFER,layer.framebuffer);
    gl.clearColor(.055,.09,.15,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.disable(gl.CULL_FACE);gl.disable(gl.DEPTH_TEST);gl.useProgram(this.program);
    gl.bindBuffer(gl.ARRAY_BUFFER,this.buffer);
    const pos=gl.getAttribLocation(this.program,'position'),uv=gl.getAttribLocation(this.program,'uv');
    gl.enableVertexAttribArray(pos);gl.vertexAttribPointer(pos,2,gl.FLOAT,false,16,0);gl.enableVertexAttribArray(uv);gl.vertexAttribPointer(uv,2,gl.FLOAT,false,16,8);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,this.texture);gl.uniform1i(gl.getUniformLocation(this.program,'screen'),0);
    gl.uniformMatrix4fv(gl.getUniformLocation(this.program,'model'),false,panelMatrix(this.panel));
    for(const view of pose.views){const v=layer.getViewport(view);gl.viewport(v.x,v.y,v.width,v.height);gl.uniformMatrix4fv(gl.getUniformLocation(this.program,'projection'),false,view.projectionMatrix);gl.uniformMatrix4fv(gl.getUniformLocation(this.program,'view'),false,view.transform.inverse.matrix);gl.drawArrays(gl.TRIANGLE_STRIP,0,4);}
    if(this.current && time-this.lastSample>=33){this.lastSample=time;const direction=rotate(orientation,[0,0,-1]),hit=intersectPanel(origin,direction,this.panel);this.onSample({frameId:this.current.id,source:'head',fallbackReason:'webxr_head_only',valid:true,u:hit?.u??null,v:hit?.v??null,origin,direction,clientMonoMs:time});}
  }
}
function createProgram(gl){
  const vertex='attribute vec2 position; attribute vec2 uv; varying vec2 vUV; uniform mat4 projection; uniform mat4 view; uniform mat4 model; void main(){vUV=uv;gl_Position=projection*view*model*vec4(position,0.,1.);}';
  const fragment='precision mediump float; varying vec2 vUV; uniform sampler2D screen; void main(){gl_FragColor=texture2D(screen,vUV);}';
  const program=gl.createProgram();
  for(const [type,source] of [[gl.VERTEX_SHADER,vertex],[gl.FRAGMENT_SHADER,fragment]]){const shader=gl.createShader(type);gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(shader));gl.attachShader(program,shader);gl.deleteShader(shader);}
  gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(program));return program;
}
