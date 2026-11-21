import { useEffect, useRef, useState } from 'react';
import { Drawer } from 'antd';
import { Menu, MessageCircle, PenSquare, ArrowUp, Square, Search, Sparkles } from 'lucide-react';
type Session={id:string;messages:{role:'user'|'assistant';text:string}[];draft:string};
const empty=():Session=>({id:crypto.randomUUID(),messages:[],draft:''});
export default function MobileChat({visible}:{visible:boolean}){
 const [sessions,setSessions]=useState<Session[]>(()=>[empty()]),[active,setActive]=useState(''),[opened,setOpened]=useState(false),[query,setQuery]=useState(''),[busy,setBusy]=useState(false);
 const timer=useRef<ReturnType<typeof setTimeout>>(),scroll=useRef<HTMLDivElement>(null);
 const current=sessions.find(s=>s.id===active)||sessions[0];
 const stop=()=>{clearTimeout(timer.current);setBusy(false)};
 useEffect(()=>()=>clearTimeout(timer.current),[]);
 useEffect(()=>{if(!visible)setOpened(false)},[visible]);
 useEffect(()=>{if(visible&&scroll.current)scroll.current.scrollTop=scroll.current.scrollHeight},[current.messages,busy,visible]);
 const choose=(id:string)=>{stop();setActive(id);setOpened(false)};
 const create=()=>{const unused=sessions.find(s=>!s.messages.length&&!s.draft);if(unused){choose(unused.id);return}const s=empty();setSessions(all=>[s,...all]);choose(s.id)};
 const title=(s:Session)=>s.messages[0]?.text||'新对话';
 const send=()=>{const text=current.draft.trim(),id=current.id;if(!text||busy)return;setSessions(all=>all.map(s=>s.id===id?{...s,draft:'',messages:[...s.messages,{role:'user',text}]}:s));setBusy(true);timer.current=setTimeout(()=>{setSessions(all=>all.map(s=>s.id===id?{...s,messages:[...s.messages,{role:'assistant',text:'当前为问答体验模式，真实模型尚未接入，暂时无法回答这个问题。'}]}:s));setBusy(false)},650)};
 return <section className="m-chat-page" aria-label="智能问答"><div className="m-chat-toolbar"><button aria-label="打开对话列表" aria-expanded={opened} onClick={()=>{setQuery('');setOpened(true)}}><Menu size={24}/></button><span>小日常问答</span><button aria-label="新建对话" onClick={create}><PenSquare size={23}/></button></div><div className="m-chat-messages" ref={scroll} aria-live="polite">{current.messages.length?current.messages.map((m,i)=><article key={i} className={`m-chat-message m-chat-${m.role}`}><small>{m.role==='user'?'我':'小日常'}</small><p>{m.text}</p></article>):<div className="m-chat-welcome"><div><Sparkles size={36}/></div><h2>你好，今天想聊些什么？</h2></div>}{busy&&<p className="mobile-footnote">正在准备回复…</p>}</div><form className="m-chat-composer" onSubmit={e=>{e.preventDefault();send()}}><textarea aria-label="消息" placeholder="输入你想说的话…" value={current.draft} maxLength={2000} rows={2} onChange={e=>setSessions(all=>all.map(s=>s.id===current.id?{...s,draft:e.target.value}:s))}/><div><small>体验模式 · 会话在本次打开期间保留</small>{busy?<button type="button" aria-label="停止回复" onClick={stop}><Square size={17}/></button>:<button type="submit" aria-label="发送消息" disabled={!current.draft.trim()}><ArrowUp size={21}/></button>}</div></form><Drawer title="对话" placement="left" open={opened&&visible} onClose={()=>setOpened(false)} width="min(330px, 88vw)" rootClassName="m-chat-drawer"><button className="m-chat-new" onClick={create}><PenSquare size={18}/>新建对话</button><div className="m-chat-search"><Search size={17}/><input aria-label="搜索对话" placeholder="搜索对话" value={query} onChange={e=>setQuery(e.target.value)}/></div><p className="m-chat-list-label">本次对话</p><div className="m-chat-session-list">{sessions.filter(s=>title(s).toLowerCase().includes(query.toLowerCase())).map(s=><button key={s.id} aria-current={s.id===current.id?'true':undefined} onClick={()=>choose(s.id)}><MessageCircle size={17}/><span>{title(s)}</span><small>{s.messages.filter(m=>m.role==='user').length} 条</small></button>)}{!sessions.some(s=>title(s).toLowerCase().includes(query.toLowerCase()))&&<p className="m-chat-list-label">没有找到匹配的对话</p>}</div></Drawer></section>
}

