import {useEffect,useRef,useState} from 'react';
import type {ButtonHTMLAttributes} from 'react';
import './companion.css';
import SproutCat,{type CatMood} from './SproutCat';

export const petDefaults={pet:'🐱',showPet:true,petName:'',petSize:'标准',petIdle:true,petSound:false,petPostCare:false,petLeft:false,petHeight:0.84};
export type PetPrefs=typeof petDefaults;

function chime(){
  try {
    const ctx=new AudioContext(),osc=ctx.createOscillator(),gain=ctx.createGain();
    osc.connect(gain);gain.connect(ctx.destination);osc.frequency.setValueAtTime(620,ctx.currentTime);
    gain.gain.setValueAtTime(.045,ctx.currentTime);gain.gain.exponentialRampToValueAtTime(.001,ctx.currentTime+.13);
    osc.onended=()=>{void ctx.close()};osc.start();osc.stop(ctx.currentTime+.14);
  } catch { /* Audio is optional on devices without Web Audio. */ }
}
type Props={prefs:PetPrefs;onPet?:()=>void;ignoreClick?:()=>boolean;mood?:CatMood} & ButtonHTMLAttributes<HTMLButtonElement>;
export function CompanionFace({prefs,onPet,ignoreClick,mood='idle',...button}:Props){
  const [active,setActive]=useState(false),timer=useRef<ReturnType<typeof setTimeout>>();
  useEffect(()=>()=>clearTimeout(timer.current),[]);
  const pose=mood==='idle'&&active?'nuzzle':mood;
  return <button {...button} type="button" className="pet-face" aria-label={`摸摸${prefs.petName||'芽芽猫'}`} data-mood={pose} data-idle={prefs.petIdle&&pose==='idle'} data-active={pose==='nuzzle'} onClick={e=>{
    if(e.detail!==0&&ignoreClick?.())return;
    clearTimeout(timer.current);setActive(true);timer.current=setTimeout(()=>setActive(false),850);
    if(prefs.petSound&&!active)chime();onPet?.();
  }}><span><SproutCat mood={pose} size={prefs.petSize==='小'?40:prefs.petSize==='大'?54:48}/></span></button>;
}

export function CompanionSettings({prefs,change}:{prefs:PetPrefs;change:(patch:Partial<PetPrefs>)=>void}){
  return <section className="m-section pet-settings"><h2>桌宠伙伴</h2><div className="pet-preview"><CompanionFace prefs={prefs}/><strong>{prefs.petName||'芽芽猫'}</strong></div>
    <label>伙伴昵称<input value={prefs.petName} placeholder="使用形象名称" maxLength={12} onChange={e=>change({petName:e.target.value})}/></label>
    <label>显示大小<select value={prefs.petSize} onChange={e=>change({petSize:e.target.value})}>{['小','标准','大'].map(s=><option key={s}>{s}</option>)}</select></label>
    <label>动作频率<select value={Number(prefs.petIdle)} onChange={e=>change({petIdle:e.target.value==='1'})}><option value="0">仅触摸时</option><option value="1">偶尔动一动</option></select></label>
    <label>触摸音效<input type="checkbox" checked={prefs.petSound} onChange={e=>change({petSound:e.target.checked})}/></label>
    <label>让芽芽猫读贴文、关心我<input type="checkbox" checked={prefs.petPostCare} aria-describedby="pet-memory-note" onChange={e=>change({petPostCare:e.target.checked})}/></label>
    <small id="pet-memory-note">登录并开启后，轻点芽芽猫会把一条可见贴文的片段（最多240字）发给模型，生成只在本次互动显示的关心话语。</small>
    <label>显示桌宠<input type="checkbox" checked={prefs.showPet} onChange={e=>change({showPet:e.target.checked})}/></label>
    <button onClick={()=>change({petLeft:petDefaults.petLeft,petHeight:petDefaults.petHeight})}>恢复默认位置</button>
  </section>;
}