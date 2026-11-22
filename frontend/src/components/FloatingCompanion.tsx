import {useEffect,useRef,useState} from 'react';
import {Fish,Moon,Sun} from 'lucide-react';
import {CompanionFace,type PetPrefs} from './CompanionFace';
import {lines,personalLine,type PetAction} from './companionLines';
import type {LocalPost} from './MobileRecords';

const clamp=(v:number,max:number)=>Math.min(Math.max(0,max),Math.max(0,v));
export default function FloatingCompanion({prefs,change,records}:{prefs:PetPrefs;change:(p:Partial<PetPrefs>)=>void;records:LocalPost[]}){
  const [speech,setSpeech]=useState(''),request=useRef<AbortController>(),expiry=useRef<ReturnType<typeof setTimeout>>(),serial=useRef<Partial<Record<PetAction,number>>>({});
  const silence=()=>{request.current?.abort();clearTimeout(expiry.current);setSpeech('')};
  useEffect(()=>()=>{request.current?.abort();clearTimeout(expiry.current)},[]);
  function say(action:PetAction){
    silence();const n=serial.current[action]??0;serial.current[action]=n+1;
    setSpeech(lines[action][n%lines[action].length]);expiry.current=setTimeout(silence,6500);
    if(action!=='摸摸'||!prefs.petPostCare)return;
    clearTimeout(expiry.current);expiry.current=setTimeout(silence,15000);
    const ctl=new AbortController();request.current=ctl;
    void personalLine(records,ctl.signal).then(line=>{if(!ctl.signal.aborted&&line){setSpeech(line);clearTimeout(expiry.current);expiry.current=setTimeout(silence,10000)}}).catch(()=>{/* Keep the local line when offline. */});
  }
  const area=useRef<HTMLDivElement>(null),dock=useRef<HTMLDivElement>(null);
  const drag=useRef<{id:number;x:number;y:number;left:number;top:number}>(),moved=useRef(false);
  const [pos,setPos]=useState<{x:number;y:number;tilt:number}>(),[tools,setTools]=useState(false);
  const [reaction,setReaction]=useState<'idle'|'snack'|'play'>('idle');
  useEffect(()=>{if(reaction==='idle')return;const timer=setTimeout(()=>setReaction('idle'),1800);return()=>clearTimeout(timer)},[reaction]);
  const [until,setUntil]=useState(0),[seconds,setSeconds]=useState(30);
  useEffect(()=>{
    if(!until)return;
    const tick=()=>{const n=Math.ceil((until-Date.now())/1000);if(n<=0){setUntil(0);setTools(false)}else setSeconds(n)};
    tick();const timer=setInterval(tick,250);return()=>clearInterval(timer);
  },[until]);
  useEffect(()=>{
    if(!tools)return;
    const close=()=>setTools(false),outside=(e:PointerEvent)=>{if(!dock.current?.contains(e.target as Node))close()},key=(e:KeyboardEvent)=>{if(e.key==='Escape'){close();dock.current?.querySelector('button')?.focus()}};
    document.addEventListener('pointerdown',outside);document.addEventListener('keydown',key);
    return()=>{document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',key)};
  },[tools]);
  return <div className="pet-area" ref={area}><div ref={dock} className="pet-dock" data-left={prefs.petLeft} style={pos?{left:pos.x,top:pos.y,rotate:`${pos.tilt}deg`}:{[prefs.petLeft?'left':'right']:0,top:`calc(${100*prefs.petHeight}% - ${54*prefs.petHeight}px)`}}>
    {speech&&<div role="status" className="pet-speech" data-below={prefs.petHeight<0.35}>{speech}</div>}
    <CompanionFace prefs={prefs} mood={until?'sleep':reaction} aria-expanded={tools} ignoreClick={()=>moved.current} onPet={()=>{setTools(true);if(!until)say('摸摸')}} onPointerDown={e=>{
      if(!e.isPrimary||e.button!==0)return;
      e.currentTarget.setPointerCapture(e.pointerId);moved.current=false;
      drag.current={id:e.pointerId,x:e.clientX,y:e.clientY,left:dock.current!.offsetLeft,top:dock.current!.offsetTop};
    }} onPointerMove={e=>{
      const d=drag.current;if(!d||d.id!==e.pointerId)return;
      const dx=e.clientX-d.x,dy=e.clientY-d.y;if(!moved.current&&Math.hypot(dx,dy)<7)return;
      if(!moved.current)silence();moved.current=true;setTools(false);const a=area.current!;
      setPos({x:clamp(d.left+dx,a.clientWidth-54),y:clamp(d.top+dy,a.clientHeight-54),tilt:Math.sign(dx)*8});
    }} onPointerUp={e=>{
      const d=drag.current;if(!d||d.id!==e.pointerId)return;
      if(moved.current){const a=area.current!,range=Math.max(1,a.clientHeight-54);change({petLeft:d.left+e.clientX-d.x<(a.clientWidth-54)/2,petHeight:clamp(d.top+e.clientY-d.y,range)/range})}
      drag.current=undefined;setPos(undefined);
    }} onPointerCancel={()=>{drag.current=undefined;moved.current=true;setPos(undefined)}}/>
    {tools&&<div className="pet-tools" role="group" aria-label="和伙伴玩" onClickCapture={e=>{const label=(e.target as Element).closest('button')?.getAttribute('aria-label');if(label==='叫醒')say('叫醒');if(label==='喂小鱼干')say('喂食');if(label==='玩绒球')say('玩球');if(label==='打个盹')say('打盹')}}>{until?<button title="叫醒" aria-label="叫醒" onClick={()=>{setUntil(0);setTools(false)}}><Sun size={18}/>{seconds}s</button>:<>
      <button title="喂小鱼干" aria-label="喂小鱼干" onClick={()=>{setReaction('snack');setTools(false)}}><Fish/></button>
      <button title="玩绒球" aria-label="玩绒球" onClick={()=>{setReaction('play');setTools(false)}}><svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><circle cx="11" cy="11" r="8"/><path d="M6 6q7 2 11 10M4 10q6 2 9 8m2-14q1 4-2 7m4 6q1 6 6 3"/></svg></button>
      <button title="打个盹" aria-label="打个盹" onClick={()=>{setReaction('idle');setSeconds(30);setUntil(Date.now()+30000)}}><Moon/></button>
    </>}</div>}
  </div></div>;
}