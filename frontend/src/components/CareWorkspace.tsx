import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { Alert, Button, Checkbox, Input, Popconfirm, Select, Space, Table, Tabs, Tag } from 'antd';
import { request, login, register, fetchMe } from '../api';
import { useAuthStore } from '../store/authStore';
import type { LocalPost } from './MobileRecords';
import type { AssessmentRecord } from './MobileAssessment';
import { RefreshCw } from 'lucide-react';
import './mobile-booking.css';
import {enableLive,stopLive} from './liveSharing';
import { mobileCall, readSession, saveSession, watchSession } from './mobileSession';

type Row = { id:string; name:string; scheduledAt:string; state:string; patientId?:number; shared?:boolean;liveSharing?:boolean;analysisAllowed?:boolean };
type Shared = { posts:{text:string;date:string}[]; results:AssessmentRecord[] };
const labels:Record<string,string>={PENDING:'待接受',ACCEPTED:'已接受',CANCELLED:'已取消',ENDED:'已结束',REJECTED:'已拒绝'};

export function DoctorInvite() {
  const [code,setCode]=useState(''),[notice,setNotice]=useState('');
  const update=useAuthStore(s=>s.updateUser);
  const role=useAuthStore(s=>s.user?.role);
  const [busy,setBusy]=useState(false);
  if(role==='doctor')return <Navigate to="/appointments" replace/>;
  return <section className="pro-certification"><form className="pro-panel" onSubmit={async e=>{
    e.preventDefault();if(busy)return;setBusy(true);setNotice('');
    try{await request('/api/care/certification',{method:'POST',body:JSON.stringify({code:code.trim()})});update(await fetchMe())}
    catch(err){setNotice(String(err))}finally{setBusy(false)}
  }}><h1>医生认证</h1><label htmlFor="doctor-invite">医生邀请码</label><Input.Password id="doctor-invite" required value={code} onChange={e=>setCode(e.target.value)}/>{notice&&<p role="alert">{notice}</p>}<Button htmlType="submit" type="primary" disabled={!code.trim()} loading={busy}>认证</Button></form></section>;
}

export default function CareWorkspace({mobile=false,account=false,posts=[],results=[]}:{mobile?:boolean;account?:boolean;posts?:LocalPost[];results?:AssessmentRecord[]}) {
  const [session,setSession]=useState(()=>mobile?readSession():null);
  const [newPassword,setNewPassword]=useState('');
  useEffect(()=>{if(mobile)return watchSession(()=>setSession(readSession()))},[mobile]);
  const [username,setUsername]=useState(''),[password,setPassword]=useState(''),[signup,setSignup]=useState(false);
  const [rows,setRows]=useState<Row[]>([]),[doctors,setDoctors]=useState<{id:number;name:string}[]>([]),[doctor,setDoctor]=useState<number>(),[time,setTime]=useState('');
  const [postIds,setPostIds]=useState<string[]>([]),[scores,setScores]=useState<number[]>([]),[consent,setConsent]=useState(false);
  const [msg,setMsg]=useState(''),[busy,setBusy]=useState(false),[version,setVersion]=useState(0),[shared,setShared]=useState<Shared|null>(null);
  const [filter,setFilter]=useState('ALL'),[loading,setLoading]=useState(false);
  const call=<T,>(path:string,method='GET',data?:unknown)=>mobile?mobileCall<T>('/api/care/'+path,method,data):request<T>('/api/care/'+path,{method,body:data===undefined?undefined:JSON.stringify(data)});
  const run=async(f:()=>Promise<void>)=>{setBusy(true);setMsg('');try{await f()}catch(e){setMsg(String(e))}finally{setBusy(false)}};
  useEffect(()=>{let active=true;setShared(null);setRows([]);if(!account&&(!mobile||session)){setLoading(true);Promise.all([call<Row[]>('appointments'),mobile?call<{id:number;name:string}[]>('doctors'):Promise.resolve([])]).then(([r,d])=>{if(active){setRows(r);setDoctors(d)}}).catch(e=>{if(active)setMsg(String(e))}).finally(()=>{if(active)setLoading(false)})}return()=>{active=false}},[session,version,mobile,account]);
  const action=(id:string,verb:string)=>run(async()=>{setShared(null);await call(`appointments/${id}/${verb}`,'POST');setVersion(v=>v+1)});
  const notice=msg&&<Alert showIcon message={msg}/>;
  const snapshot={posts:posts.filter(p=>postIds.includes(p.id)).map(p=>({text:p.text||p.title,date:p.date})),results:results.filter((_,i)=>scores.includes(i)).map(({name,score,date})=>({name,score,date}))};
  const hasRecords=!!(snapshot.posts.length||snapshot.results.length);
  if(!mobile){
    const visible=rows.filter(r=>filter==='ALL'||r.state===filter||(filter==='CLOSED'&&['ENDED','CANCELLED','REJECTED'].includes(r.state)));
    return <section className="pro-page"><div className="pro-page-heading"><h1>预约管理</h1></div>
      {msg&&<Alert type="error" message={msg} showIcon/>}
      <Tabs activeKey={filter} onChange={setFilter} items={[['ALL','全部'],['PENDING','待接受'],['ACCEPTED','已接受'],['CLOSED','已关闭']].map(([key,label])=>({key,label}))}/>
      <Table<Row> rowKey="id" loading={loading} dataSource={visible} scroll={{x:680}} locale={{emptyText:'暂无符合条件的预约'}} pagination={{pageSize:12,hideOnSinglePage:true}} columns={[
        {title:'用户',dataIndex:'name',render:(name,r)=><div><strong>{name}</strong><div className="pro-muted">账号 {r.patientId}</div></div>},
        {title:'预约时间',dataIndex:'scheduledAt',render:v=>new Date(v).toLocaleString('zh-CN',{dateStyle:'medium',timeStyle:'short'})},
        {title:'状态',dataIndex:'state',render:v=><Tag color={v==='PENDING'?'gold':v==='ACCEPTED'?'blue':undefined}>{labels[v]??v}</Tag>},
        {title:'操作',render:(_,r)=><Space wrap>{r.state==='PENDING'?<><Button size="small" type="primary" disabled={busy||loading} onClick={()=>action(r.id,'accept')}>接受预约</Button><Popconfirm title="拒绝这次预约？" onConfirm={()=>action(r.id,'reject')}><Button size="small" disabled={busy||loading}>拒绝</Button></Popconfirm></>:r.state==='ACCEPTED'?<>{r.shared&&<Link to="/care-users" state={{appointment:r.id}}>查看资料</Link>}<Popconfirm title="结束预约并停止共享？" onConfirm={()=>action(r.id,'end')}><Button size="small" disabled={busy||loading}>结束预约</Button></Popconfirm></>:<span aria-label="无可用操作">—</span>}</Space>},
      ]}/></section>;
  }
  if(mobile&&!session)return <section className="m-section"><h2>{signup?'注册个人账号':'个人账号登录'}</h2><form onSubmit={e=>{e.preventDefault();void run(async()=>{const r=await (signup?register:login)(username,password);if(r.user.role!=='user')throw Error('请使用移动端个人账号');saveSession({token:r.token,name:r.user.username,expires:r.expires_at});setPassword('')})}}><Input required placeholder="用户名" value={username} onChange={e=>setUsername(e.target.value)}/><Input.Password required placeholder="密码" value={password} onChange={e=>setPassword(e.target.value)}/><Button loading={busy} htmlType="submit">{signup?'注册并登录':'登录'}</Button><Button type="link" onClick={()=>setSignup(!signup)}>{signup?'已有账号':'注册个人账号'}</Button></form>{notice}</section>;
  if(account)return <section className="mobile-booking"><form onSubmit={e=>{e.preventDefault();void run(async()=>{await mobileCall('/api/auth/change-password','POST',{old_password:password,new_password:newPassword});setPassword('');setNewPassword('');saveSession(null);setMsg('密码已修改，请重新登录')})}}><h2>账号安全</h2><p>当前账号：{session?.name}</p><label>当前密码<Input.Password required autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)}/></label><label>新密码<Input.Password required autoComplete="new-password" value={newPassword} onChange={e=>setNewPassword(e.target.value)}/></label><Button htmlType="submit" loading={busy}>修改密码</Button><Button disabled={busy} onClick={()=>void run(async()=>{try{await mobileCall('/api/auth/logout','POST')}finally{setPassword('');setNewPassword('');saveSession(null)}})}>退出登录</Button></form>{notice}</section>;
  return <section className={mobile?'mobile-booking':'pro-page'}>{!mobile&&<h1>预约管理</h1>}{notice}
    {mobile&&<form onSubmit={e=>{e.preventDefault();void run(async()=>{if(!doctor||(hasRecords&&!consent))throw Error('请选择医生并核对共享内容');if(!(new Date(time).getTime()>Date.now()))throw Error('请选择未来的预约时间');await call('appointments','POST',{doctorId:doctor,scheduledAt:new Date(time).toISOString(),consent:hasRecords&&consent,snapshot});setConsent(false);setPostIds([]);setScores([]);setTime('');setMsg('预约已提交');setVersion(v=>v+1)})}}>
      <div className="booking-heading"><h2>新建预约</h2><small>账号 · {session?.name}</small></div>
      <div className="booking-field"><label htmlFor="booking-doctor">预约医生</label><Select id="booking-doctor" placeholder="选择认证医生" notFoundContent="暂无可预约医生" value={doctor} options={doctors.map(d=>({value:d.id,label:d.name}))} onChange={d=>{setDoctor(d);setConsent(false)}}/></div>
      <div className="booking-field"><label htmlFor="booking-time">预约时间</label><input id="booking-time" type="datetime-local" required value={time} onChange={e=>setTime(e.target.value)}/></div>
      <div className="booking-share"><h3>共享资料 <small>选填</small></h3>
      <details><summary>选择本机贴文（已选 {postIds.length}）</summary>{posts.map(p=><div key={p.id}><Checkbox checked={postIds.includes(p.id)} onChange={e=>{setConsent(false);setPostIds(e.target.checked?[...postIds,p.id]:postIds.filter(id=>id!==p.id))}}>{(p.text||p.title||'无正文').slice(0,100)}</Checkbox></div>)}</details>
      <details><summary>选择本机量表结果（已选 {scores.length}）</summary>{results.map((r,i)=><div key={i}><Checkbox checked={scores.includes(i)} onChange={e=>{setConsent(false);setScores(e.target.checked?[...scores,i]:scores.filter(x=>x!==i))}}>{r.name} · {r.score} 分 · {r.date}</Checkbox></div>)}</details>
      </div>{hasRecords?<><details open className="booking-share"><summary>{doctors.find(d=>d.id===doctor)?.name||'预约医生'} 将看到：{snapshot.posts.length} 条贴文、{snapshot.results.length} 项测评</summary>{snapshot.posts.map((p,i)=><p key={i} style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{p.date} · {p.text}</p>)}{snapshot.results.map((r,i)=><p key={i}>{r.date} · {r.name} · {r.score} 分</p>)}</details><Checkbox checked={consent} onChange={e=>setConsent(e.target.checked)}>确认上述资料属于我，允许医生接受本次预约后查看。此操作不授权模型分析或后续记录同步。</Checkbox></>:<p>仅提交预约，不共享贴文或测评。</p>}<Button size="large" type="primary" disabled={(hasRecords&&!consent)||!doctor||!time} loading={busy} htmlType="submit">提交预约</Button></form>}
    <div className={mobile?'booking-heading booking-history':undefined}>{mobile&&<h2>预约记录</h2>}</div>{rows.length===0&&<p className={mobile?'booking-empty':undefined}>暂无预约</p>}{rows.map(r=><section className={mobile?'booking-row':undefined} key={r.id}><h3>{r.name}</h3><p>{new Date(r.scheduledAt).toLocaleString()} · {labels[r.state]}</p><Space wrap>
      {mobile&&['PENDING','ACCEPTED'].includes(r.state)&&<Button disabled={busy} onClick={()=>action(r.id,'cancel')}>取消预约</Button>}
      {mobile&&r.state==='ACCEPTED'&&<Popconfirm title={r.analysisAllowed?'撤销本次模型分析授权？':'允许本机模型分析本次共享贴文？'} onConfirm={()=>run(async()=>{await call(`appointments/${r.id}/analysis-grant/${!r.analysisAllowed}`,'POST');setVersion(v=>v+1)})}><Button disabled={busy}>{r.analysisAllowed?'撤销模型授权':'授权模型分析'}</Button></Popconfirm>}
      {mobile&&r.state==='ACCEPTED'&&<Popconfirm title={r.liveSharing?'停止共享并清除本次共享资料？':`与 ${r.name} 持续共享？`} description={r.liveSharing?undefined:'包括本机现有和今后新增的全部贴文与测评结果，而非仅预约时勾选的记录。请确认均为本人资料。'} okText="确认" cancelText="取消" onConfirm={()=>run(async()=>{await (r.liveSharing?stopLive:enableLive)(r.id);setVersion(v=>v+1)})}><Button disabled={busy}>{r.liveSharing?'停止持续共享':'开启持续共享'}</Button></Popconfirm>}
      {!mobile&&r.state==='PENDING'&&<><Button disabled={busy} onClick={()=>action(r.id,'accept')}>接受预约</Button><Button disabled={busy} onClick={()=>action(r.id,'reject')}>拒绝</Button></>}
      {!mobile&&r.state==='ACCEPTED'&&<><Button disabled={busy} onClick={()=>run(async()=>{setShared(null);setShared(await call<Shared>(`appointments/${r.id}/records`))})}>查看共享资料</Button><Button disabled={busy} onClick={()=>action(r.id,'end')}>结束预约</Button></>}
    </Space></section>)}{shared&&<section><h2>本次预约共享资料</h2>{shared.posts.map((p,i)=><p key={i}>{p.date} · {p.text}</p>)}{shared.results.map((r,i)=><p key={i}>{r.date} · {r.name} · {r.score} 分</p>)}{!shared.posts.length&&!shared.results.length&&<p>用户未选择记录</p>}<Button onClick={()=>setShared(null)}>关闭资料</Button></section>}</section>;
}