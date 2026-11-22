import {useEffect,useState} from 'react';
import {Link2,RefreshCw} from 'lucide-react';
import {Capacitor} from '@capacitor/core';
import {mobileCall,readSession} from './mobileSession';
const api='/api/me/sync/douyin';
type Connection={status:string;lastSuccess:number;initialPages?:number;initialItems?:number;oldestCreateTime?:number;newestCreateTime?:number;upstreamHasMore?:boolean;initialFinished?:boolean};
type Status={available:boolean;connection:Connection[]};
type Page={items:{id:string;content:string;source:string;publishedAt?:string}[];total:number};
const labels:Record<string,string>={SYNCING:'正在同步',CURRENT:'最近一轮同步已完成',RETRY:'同步暂未成功，稍后重试',REAUTHORIZE:'需要重新授权'};
function WeiboConnection(){
 const endpoint='/api/me/sync/weibo';const [state,update]=useState<Status>(),[device,setDevice]=useState<{url:string;userCode:string}>(),[error,setError]=useState(''),[busy,setBusy]=useState(false),[erase,setErase]=useState(false);
 useEffect(()=>{let live=true;async function load(){try{const s=await mobileCall<Status>(endpoint);if(live)update(s)}catch(e){if(live)setError((e as Error).message)}}void load();const timer=setInterval(load,10000);return()=>{live=false;clearInterval(timer)}},[]);
 const c=state?.connection[0];
 async function change(remove:boolean){if(remove&&!confirm(erase?'断开并删除已同步的微博记录？':'停止微博同步并保留记录？'))return;setBusy(true);setError('');try{if(remove){await mobileCall(endpoint+'?erase='+erase,'DELETE');setDevice(undefined);setErase(false)}else setDevice(await mobileCall<{url:string;userCode:string}>(endpoint+'/start','POST'));update(await mobileCall<Status>(endpoint))}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 return <><h3>微博</h3>{error&&<p role="alert">{error}</p>}<p role="status">{c?({PENDING:'等待本人授权',NO_PERMISSION:'当前套餐无读取权限',...labels}[c.status]):!state?'正在读取连接状态':state.available?'未连接':'暂未开放'}</p>
 {!!c?.lastSuccess&&<p>上次同步：{new Date(c.lastSuccess).toLocaleString()}</p>}
 {device&&c?.status==='PENDING'&&<p>本人授权码：<strong>{device.userCode}</strong> · <a href={device.url} target="_blank" rel="noreferrer">前往微博开放平台确认</a></p>}
 {(!c||['PENDING','NO_PERMISSION','REAUTHORIZE'].includes(c.status))&&<><p>授权保存本人微博、媒体信息及平台返回的互动计数。</p><button disabled={!state?.available||busy} onClick={()=>change(false)}><Link2 size={16}/>{c?.status==='PENDING'?'重新获取授权码':'连接微博'}</button></>}
 {c&&<><label><input type="checkbox" checked={erase} onChange={e=>setErase(e.target.checked)}/>同时删除已同步的微博记录</label><button disabled={busy} onClick={()=>change(true)}>断开微博连接</button></>}</>;
}
export default function MobilePlatformSync({onLogin}:{onLogin:()=>void}){
 const [status,setStatus]=useState<Status>(),[error,setError]=useState(''),[page,setPage]=useState(1),[posts,setPosts]=useState<Page>(),[busy,setBusy]=useState(false),[erase,setErase]=useState(false),[revision,reload]=useState(0);
 useEffect(()=>{if(!readSession())return;let live=true;const load=async()=>{try{const [s,p]=await Promise.all([mobileCall<Status>(api),mobileCall<Page>(`/api/me/posts?page=${page}&pageSize=20`)]);if(live){setStatus(s);setPosts(p)}}catch(e){if(live)setError((e as Error).message)}};void load();const timer=setInterval(load,15000);return()=>{live=false;clearInterval(timer)}},[page,revision]);
 useEffect(()=>{const url=new URL(location.href);if(!url.searchParams.has('state'))return;const code=url.searchParams.get('code'),state=url.searchParams.get('state');for(const k of ['code','state','error','error_description','scopes'])url.searchParams.delete(k);history.replaceState(null,'',url);if(!code){setError('授权未完成');return}setBusy(true);mobileCall(api+'/finish','POST',{code,state}).then(()=>reload(n=>n+1)).catch(e=>setError(e.message)).finally(()=>setBusy(false))},[]);
 async function connect(){setBusy(true);setError('');try{const r=await mobileCall<{url:string}>(api+'/start','POST');location.assign(r.url)}catch(e){setBusy(false);setError((e as Error).message)}}
 async function stop(){if(!confirm(erase?'断开并删除此账号已同步的抖音记录？':'停止自动同步并保留已有记录？'))return;setBusy(true);try{await mobileCall(api+'?erase='+erase,'DELETE');setErase(false);setPage(1);reload(n=>n+1)}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
 const c=status?.connection[0];
 const day=(seconds:number)=>new Date(seconds*1000).toLocaleDateString();
 return <section className="m-section mobile-records">
   <h2>平台同步</h2>{error&&<p role="alert">{error}</p>}
   {!readSession()?<button onClick={onLogin}>登录个人账号</button>:<>
     <h3>抖音</h3>
     <p role="status">{c?labels[c.status]:!status?'正在读取连接状态':!status.available?'暂未开放':Capacitor.isNativePlatform()?'请通过移动网页版连接':'未连接'}</p>
     {!!c?.lastSuccess&&<p>上次同步：{new Date(c.lastSuccess).toLocaleString()}</p>}
     {!!c?.initialPages&&<p>首次同步{c.initialFinished?'范围':'进度'}：已读取 {c.initialPages} 页、{c.initialItems} 条返回记录。
       {!!c.oldestCreateTime&&!!c.newestCreateTime&&<>发布时间：{day(c.oldestCreateTime)} 至 {day(c.newestCreateTime)}。</>}
       {c.initialFinished&&c.upstreamHasMore?'已到本次页数上限，平台仍有后续记录。':'此范围不代表全部历史。'}</p>}
     {(!c||c.status==='REAUTHORIZE')&&<><p>授权将本人作品信息及平台返回的互动计数保存到个人账号，不授权医生查看或模型分析。</p><button disabled={busy||!status?.available||Capacitor.isNativePlatform()} onClick={connect}><Link2 size={16}/> 连接抖音</button></>}
     {c&&<><label><input type="checkbox" checked={erase} onChange={e=>setErase(e.target.checked)}/>同时删除已同步的抖音记录</label><button disabled={busy} onClick={stop}>断开连接</button></>}
     <WeiboConnection/><h3>账号记录 {posts?.total??''}</h3><button aria-label="刷新同步状态" onClick={()=>reload(n=>n+1)}><RefreshCw size={16}/></button>
     {posts?.items.map(p=><article className="mobile-post" key={p.id}><small>{p.source} · {p.publishedAt?new Date(p.publishedAt).toLocaleDateString():'时间未知'}</small><p>{p.content||'无作品文案'}</p></article>)}
     {posts&&<><button disabled={page===1} onClick={()=>setPage(page-1)}>上一页</button> {page} <button disabled={page*20>=posts.total} onClick={()=>setPage(page+1)}>下一页</button></>}
   </>}
 </section>;
}