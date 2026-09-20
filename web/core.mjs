export const dot = (a,b) => a.reduce((s,x,i) => s + x*b[i],0);
export const add = (a,b) => a.map((x,i) => x+b[i]);
export const scale = (a,n) => a.map(x => x*n);
export const sub = (a,b) => a.map((x,i) => x-b[i]);
export function rotate(q,v) {
  const [x,y,z,w] = Array.isArray(q) ? q : [q.x,q.y,q.z,q.w];
  const uv=[y*v[2]-z*v[1],z*v[0]-x*v[2],x*v[1]-y*v[0]];
  const uuv=[y*uv[2]-z*uv[1],z*uv[0]-x*uv[2],x*uv[1]-y*uv[0]];
  return add(v,add(scale(uv,2*w),scale(uuv,2)));
}
export function makePanel(position,orientation,width=1.8,aspect=16/9,distance=1.6) {
  const right=rotate(orientation,[1,0,0]),up=rotate(orientation,[0,1,0]),normal=rotate(orientation,[0,0,1]);
  return {center:add(position,scale(normal,-distance)),right,up,normal,width,height:width/aspect};
}
export function intersectPanel(origin,direction,panel) {
  if (![...origin,...direction].every(Number.isFinite)) return null;
  const denominator=dot(direction,panel.normal);
  if (denominator>=-1e-8) return null;
  const distance=dot(sub(panel.center,origin),panel.normal)/denominator;
  if (distance<=0) return null;
  const relative=sub(add(origin,scale(direction,distance)),panel.center);
  const u=dot(relative,panel.right)/panel.width+0.5,v=0.5-dot(relative,panel.up)/panel.height;
  return u>=0 && u<1 && v>=0 && v<1 ? {u,v,distance} : null;
}
export function panelMatrix(p) {
  return new Float32Array([...scale(p.right,p.width),0,...scale(p.up,p.height),0,...p.normal,0,...p.center,1]);
}
export function selectTracking(eye,head,mode="auto") {
  if (mode!=="head" && eye?.valid) return {...eye,source:"eye",fallbackReason:null};
  const reason=mode==="head"?"head_forced":eye?.supported?"eye_tracking_lost":"eye_tracking_unavailable";
  return {origin:head?.origin??null,direction:head?.direction??null,valid:!!head?.valid,source:"head",fallbackReason:reason};
}
export function findTarget(frame,u,v) {
  if (!frame || !Number.isFinite(u) || !Number.isFinite(v) || u<0 || u>=1 || v<0 || v>=1) return null;
  const targets=frame.targets.filter(t=>t.bounds.some(r=>u*frame.width>=r.x && u*frame.width<r.x+r.width && v*frame.height>=r.y && v*frame.height<r.y+r.height));
  return targets.length===1 ? targets[0] : null;
}
