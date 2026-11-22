import MobilePlatformSync from '../components/MobilePlatformSync';
import DailyFeed from '../components/DailyFeed';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { CalendarDays, HeartPulse, MessageCircle, UserRound, Settings, ChevronRight, FileText, ClipboardList, Stethoscope, Download, Upload, Shield, Cat, ArrowLeft, Pencil } from 'lucide-react';
import './mobile-prototype.css';
import './mobile-library.css';
import {scopedKey,watchSession} from '../components/mobileSession';
import {syncLive} from '../components/liveSharing';
import CareWorkspace from '../components/CareWorkspace';
import MobileChat from '../components/MobileChat';
import MobileRecords, { LocalPost, readRecords, saveRecords } from '../components/MobileRecords';
import FloatingCompanion from '../components/FloatingCompanion';
import {CompanionSettings,petDefaults,type PetPrefs} from '../components/CompanionFace';
import { MobileAssessment, AssessmentRecord } from '../components/MobileAssessment';
const tabs=[{id:'daily',title:'日常',Icon:CalendarDays},{id:'health',title:'健康',Icon:HeartPulse},{id:'chat',title:'问答',Icon:MessageCircle},{id:'mine',title:'我的',Icon:UserRound}];
type Profile=PetPrefs & {name:string;bio:string;avatar:string};
const defaults:Profile={...petDefaults,name:'我的小日常',bio:'认真生活，慢慢成长',avatar:'🌷'};
export default function MobilePrototypePage(){
 const [owner,setOwner]=useState(()=>scopedKey('owner'));useLayoutEffect(()=>watchSession(()=>setOwner(scopedKey('owner'))),[]);return <MobileContent key={owner}/>;
}
function MobileContent(){
 const [records,setRecords]=useState<LocalPost[]>([]),[storageError,setStorageError]=useState(''),[tab,setTab]=useState('daily'),[panel,setPanel]=useState(''),[profile,setProfile]=useState(defaults),[draft,setDraft]=useState(defaults),[history,setHistory]=useState<AssessmentRecord[]>([]),[notice,setNotice]=useState('');
 useEffect(()=>{try{setRecords(readRecords());const p=JSON.parse(localStorage.getItem(scopedKey('vis4srd.mobile-profile.v1'))||'null');if(p&&typeof p.name==='string'&&typeof p.bio==='string'&&typeof p.avatar==='string'&&typeof p.pet==='string'&&typeof p.showPet==='boolean'){setProfile({...defaults,...p});setDraft({...defaults,...p})}const h=JSON.parse(localStorage.getItem(scopedKey('vis4srd.mobile-assessments.v1'))||'[]');if(Array.isArray(h))setHistory(h.filter(r=>r&&typeof r.date==='string'&&typeof r.name==='string'&&typeof r.score==='number'))}catch{setStorageError('本机记录读取失败，请检查存储设置。')}},[]);
 const sync=()=>{void syncLive().catch(()=>setNotice('持续共享未同步，请检查网络后重新打开应用。'))};
 useEffect(()=>{sync();const timer=window.setInterval(sync,30000);window.addEventListener('online',sync);return()=>{window.clearInterval(timer);window.removeEventListener('online',sync)}},[]);
 const saveProfile=(p:Profile,quiet=false)=>{try{localStorage.setItem(scopedKey('vis4srd.mobile-profile.v1'),JSON.stringify(p));setProfile(p);if(!quiet)setNotice('已保存')}catch{setNotice('保存失败，请检查本机存储')}};
 const changePet=(patch:Partial<PetPrefs>)=>saveProfile({...profile,...patch},true);
 const updateRecords=(next:LocalPost[])=>{if(storageError)throw Error(storageError);saveRecords(next);setRecords(next);sync()};
 const main=useRef<HTMLElement>(null), trail=useRef<{panel:string;top:number}[]>([]), restore=useRef(0);
 const open=(p:string)=>{
   if(p===panel)return;
   if(p){trail.current.push({panel,top:main.current?.scrollTop??0});restore.current=0;setPanel(p)}
   else {const prev=trail.current.pop();restore.current=prev?.top??0;setPanel(prev?.panel??'')}
   setNotice('');
 };
 useLayoutEffect(()=>{if(main.current)main.current.scrollTop=restore.current;restore.current=0},[panel,tab]);
 useEffect(()=>{if(location.pathname.endsWith('/sync')||new URL(location.href).searchParams.has('state'))setPanel('imports')},[]);
 useEffect(()=>{const old=document.title;document.title='小日常';return()=>{document.title=old}},[]);
 const titles:Record<string,string>={profile:'编辑资料',records:'日常记录',weibo:'导入微博',assess:'心理测评',history:'测评记录',doctors:'联系医生',appointments:'我的预约',settings:'设置',privacy:'隐私与安全',imports:'一键导入',platforms:'平台授权',account:'账号安全',pet:'桌宠伙伴',about:'关于小日常'};
 const backup=()=>{const blob=new Blob([JSON.stringify(records,null,2)],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='我的日常记录.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);setNotice('已导出日常记录')};
 const entries=[{name:'测评记录',icon:ClipboardList,action:()=>open('history')},{name:'我的预约',icon:Stethoscope,action:()=>open('appointments')},{name:'桌宠伙伴',icon:Cat,action:()=>open('pet')}];
return <div className={`mobile-prototype m-redesign ${tab==='chat'&&!panel?'m-chat-active':''}`}>{panel&&<header><button className="m-back" aria-label={`返回，当前为${titles[panel]}`} onClick={()=>open('')}><ArrowLeft size={20}/>返回</button></header>}<main ref={main}>{storageError&&<p role="alert">{storageError}</p>}{notice&&<p role="status" className="m-notice">{notice}</p>}
  {!panel&&tab==='daily'&&<DailyFeed local={records}/>}
  {panel==='imports'&&<MobilePlatformSync onLogin={()=>open('account')}/>}
 <div hidden={!!panel||tab!=='chat'}><MobileChat visible={!panel && tab === "chat"}/></div>
 {!panel&&tab==='mine'&&<><section className="m-profile"><button className="m-avatar" onClick={()=>{setDraft(profile);open('profile')}}>{profile.avatar}</button><div><h2>{profile.name}<button aria-label="编辑资料" onClick={()=>{setDraft(profile);open('profile')}}><Pencil size={15}/></button></h2><p>{profile.bio}</p><span className="m-local-label">本机个人资料</span></div></section><div className="m-icon-grid" style={{gridTemplateColumns:'repeat(3,1fr)'}}>{entries.map(e=><button key={e.name} onClick={e.action}><e.icon size={25}/><span>{e.name}</span></button>)}</div><section className="m-section"><h2>更多服务</h2>{[{name:'隐私与安全',icon:Shield,p:'privacy'},{name:'设置',icon:Settings,p:'settings'},{name:'关于小日常',icon:HeartPulse,p:'about'}].map(e=><button className="m-menu-row" key={e.p} onClick={()=>open(e.p)}><e.icon/><span>{e.name}</span><ChevronRight size={18}/></button>)}</section></>}
 {panel==='profile'&&<form className="mobile-records m-section" onSubmit={e=>{e.preventDefault();saveProfile(draft)}}><div className="m-avatar-options">{['🌷','🌻','🌿','🐱','🐰','🐻'].map(a=><button type="button" key={a} aria-pressed={draft.avatar===a} onClick={()=>setDraft({...draft,avatar:a})}>{a}</button>)}</div><label>昵称<input required maxLength={20} value={draft.name} onChange={e=>setDraft({...draft,name:e.target.value})}/></label><label>个人签名<input maxLength={60} value={draft.bio} onChange={e=>setDraft({...draft,bio:e.target.value})}/></label><button className="m-primary" disabled={!draft.name.trim()}>保存资料</button></form>}
 {panel==='records'&&<MobileRecords posts={records} onSave={updateRecords}/>}
 {(panel==='assess'||(!panel&&tab==='health'))&&<MobileAssessment history={history} onComplete={r=>{const next=[r,...history].slice(0,100);localStorage.setItem(scopedKey('vis4srd.mobile-assessments.v1'),JSON.stringify(next));setHistory(next);sync()}}/>}
 {panel==='history'&&<section className="m-section">{history.length?history.map((r,i)=><article className="m-history" key={i}><ClipboardList/><div><strong>{r.name}</strong><p>{new Date(r.date).toLocaleString()}</p></div><b>{r.score} 分</b></article>):<div className="m-empty"><ClipboardList size={38}/><h2>还没有测评记录</h2><p>完成测评后，可以在这里回顾。</p><button className="m-primary" onClick={()=>open('assess')}>去测评</button></div>}</section>}
 {(panel==='appointments'||panel==='doctors')&&<CareWorkspace mobile posts={records} results={history}/>}
 {panel==='pet'&&<CompanionSettings prefs={profile} change={changePet}/>}
 {panel==='settings'&&<section className="m-section"><button className="m-menu-row" onClick={()=>open('account')}><Shield/><span>账号安全</span><ChevronRight size={18}/></button></section>}
 {panel==='account'&&<CareWorkspace mobile account/>}
 {panel==='privacy'&&<section className="m-section">
   <button className="m-menu-row" onClick={()=>open('imports')}><Upload/><span>一键导入</span><ChevronRight size={18}/></button>
   <button className="m-menu-row" onClick={backup}><Download/><span>导出本机记录</span><ChevronRight size={18}/></button>
   {records.length>0&&<button className="m-menu-row" onClick={()=>open('records')}><FileText/><span>历史本机记录</span><ChevronRight size={18}/></button>}
 </section>}
 {panel==='about'&&<section className="m-section"><h2>小日常</h2><p>记录生活，关照情绪，让支持更容易找到。</p></section>}
 </main>{profile.showPet&&panel!=='pet'&&panel!=='profile'&&<FloatingCompanion key={`${tab}:${panel}`} prefs={profile} records={records} change={changePet}/>}<nav aria-label="移动端底部导航">{tabs.map(({id,title,Icon})=><button key={id} aria-current={tab===id?'page':undefined} onClick={()=>{trail.current=[];restore.current=0;setTab(id);setPanel('');setNotice('')}}><Icon size={23}/><span>{title}</span></button>)}</nav></div>
}

