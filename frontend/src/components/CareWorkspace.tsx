import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { Alert, Button, Input, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { request, login, register, fetchMe } from '../api';
import { useAuthStore } from '../store/authStore';
import type { LocalPost } from './MobileRecords';
import type { AssessmentRecord } from './MobileAssessment';
import { CalendarPlus, ChevronDown } from 'lucide-react';
import './mobile-booking.css';
import './mobile-account.css';
import {bookingSnapshot} from './liveSharing';
import { mobileCall, readSession, saveSession, watchSession } from './mobileSession';

type Row = { id:string; name:string; scheduledAt:string; state:string; patientId?:number; shared?:boolean; };
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

export default function CareWorkspace({mobile=false,account=false,posts=[],results=[],filter='ALL'}:{mobile?:boolean;account?:boolean;posts?:LocalPost[];results?:AssessmentRecord[];filter?:string}) {
  const [session,setSession]=useState(()=>mobile?readSession():null);
  const [newPassword,setNewPassword]=useState('');
  const [confirmation,setConfirmation]=useState('');
  useEffect(()=>{if(mobile)return watchSession(()=>setSession(readSession()))},[mobile]);
  const [username,setUsername]=useState(''),[password,setPassword]=useState(''),[signup,setSignup]=useState(false);
  const [rows,setRows]=useState<Row[]>([]),[doctors,setDoctors]=useState<{id:number;name:string}[]>([]),[doctor,setDoctor]=useState<number>(),[time,setTime]=useState('');
  const [bookingOpen,setBookingOpen]=useState(false);
  const [msg,setMsg]=useState(''),[busy,setBusy]=useState(false),[version,setVersion]=useState(0),[shared,setShared]=useState<Shared|null>(null);
  const [loading,setLoading]=useState(false);
  const call=<T,>(path:string,method='GET',data?:unknown)=>mobile?mobileCall<T>('/api/care/'+path,method,data):request<T>('/api/care/'+path,{method,body:data===undefined?undefined:JSON.stringify(data)});
  const run=async(f:()=>Promise<void>)=>{setBusy(true);setMsg('');try{await f()}catch(e){setMsg(String(e))}finally{setBusy(false)}};
  useEffect(()=>{let active=true;setShared(null);setRows([]);if(!account&&(!mobile||session)){setLoading(true);Promise.all([call<Row[]>('appointments'),mobile?call<{id:number;name:string}[]>('doctors'):Promise.resolve([])]).then(([r,d])=>{if(active){setRows(r);setDoctors(d)}}).catch(e=>{if(active)setMsg(String(e))}).finally(()=>{if(active)setLoading(false)})}return()=>{active=false}},[session,version,mobile,account]);
  const action=(id:string,verb:string)=>run(async()=>{setShared(null);await call(`appointments/${id}/${verb}`,'POST');setVersion(v=>v+1)});
  const notice=msg&&<Alert showIcon message={msg}/>;
  const snapshot={posts:posts.map(p=>({text:p.text||p.title,date:p.date})),results:results.map(({name,score,date})=>({name,score,date}))};
  const bookingIssue=()=>{
    if(!doctor)return loading?'正在加载医生名单':!doctors.length?'没有可选择的医生，请检查医生名单是否已加载':'请先选择预约医生';
    if(!time)return '请选择预约日期和时间';
    if(!(new Date(time).getTime()>Date.now()))return '请选择未来的预约时间';
    if(snapshot.posts.length>100||snapshot.results.length>100)return '每次最多共享 100 条贴文和 100 项测评';
    if(snapshot.posts.some(p=>!p.text?.trim()||p.text.length>8000||(p.date?.length??0)>80))return '无法共享全部资料：贴文正文须为 1 至 8000 字，日期最多 80 字';
    return '';
  };
  if(!mobile){
    const visible=rows.filter(r=>filter==='ALL'||r.state===filter||(filter==='CLOSED'&&['ENDED','CANCELLED','REJECTED'].includes(r.state)));
    return <section className="pro-page">
      {msg&&<Alert type="error" message={msg} showIcon/>}
      <Table<Row> rowKey="id" loading={loading} dataSource={visible} scroll={{x:680}} locale={{emptyText:'暂无符合条件的预约'}} pagination={{pageSize:12,hideOnSinglePage:true}} columns={[
        {title:'用户',dataIndex:'name',render:(name,r)=><div><strong>{name}</strong><div className="pro-muted">账号 {r.patientId}</div></div>},
        {title:'预约时间',dataIndex:'scheduledAt',render:v=>new Date(v).toLocaleString('zh-CN',{dateStyle:'medium',timeStyle:'short'})},
        {title:'状态',dataIndex:'state',render:v=><Tag color={v==='PENDING'?'gold':v==='ACCEPTED'?'blue':undefined}>{labels[v]??v}</Tag>},
        {title:'操作',render:(_,r)=><Space wrap>{r.state==='PENDING'?<><Button size="small" type="primary" disabled={busy||loading} onClick={()=>action(r.id,'accept')}>接受预约</Button><Popconfirm title="拒绝这次预约？" onConfirm={()=>action(r.id,'reject')}><Button size="small" disabled={busy||loading}>拒绝</Button></Popconfirm></>:r.state==='ACCEPTED'?<>{r.shared&&<Link to="/care-users" state={{appointment:r.id}}>查看资料</Link>}<Popconfirm title="结束预约并停止共享？" onConfirm={()=>action(r.id,'end')}><Button size="small" disabled={busy||loading}>结束预约</Button></Popconfirm></>:<span aria-label="无可用操作">—</span>}</Space>},
      ]}/></section>;
  }
  if(!session || account){
    const changing=!!session;
    const creating=!changing && signup;
    const needsConfirmation=changing || creating;
    const mismatch=needsConfirmation && confirmation!=='' && confirmation!==(changing?newPassword:password);
    const submit=changing?'保存新密码':creating?'注册并登录':'登录';
    return <section className="mobile-account">
      <p className="account-caption">{changing?'账号安全':'小日常 · 个人账号'}</p>
      <h2>{changing?'修改密码':creating?'注册个人账号':'个人账号登录'}</h2>
      {changing && <p className="account-owner">当前账号：{session.name}</p>}
      <form onSubmit={e=>{e.preventDefault();if(busy || mismatch)return;void run(async()=>{
        if(changing){
          await mobileCall('/api/auth/change-password','POST',{old_password:password,new_password:newPassword});
          setPassword('');setNewPassword('');setConfirmation('');saveSession(null);
        }else{
          const r=await (creating?register:login)(username.trim(),password);
          if(r.user.role!=='user')throw Error('请使用移动端个人账号');
          setPassword('');setConfirmation('');saveSession({token:r.token,name:r.user.username,expires:r.expires_at});
        }
      })}}>
        {!changing && <label>用户名<Input name="username" autoComplete="username" autoCapitalize="none" spellCheck={false} required value={username} onChange={e=>setUsername(e.target.value)}/></label>}
        <label>{changing?'当前密码':'密码'}<Input.Password name="password" required autoComplete={creating?'new-password':'current-password'} value={password} onChange={e=>setPassword(e.target.value)}/></label>
        {changing && <label>新密码<Input.Password name="new-password" required autoComplete="new-password" value={newPassword} onChange={e=>setNewPassword(e.target.value)}/></label>}
        {needsConfirmation && <label>{changing?'确认新密码':'确认密码'}<Input.Password name="confirmation" required autoComplete="new-password" aria-invalid={mismatch} aria-describedby={mismatch?'account-mismatch':undefined} value={confirmation} onChange={e=>setConfirmation(e.target.value)}/></label>}
        {mismatch && <p id="account-mismatch" role="status">两次密码不一致，请重新核对。</p>}
        {msg && <Alert showIcon type="error" message={msg}/>}
        {changing && <small>保存后需要使用新密码重新登录。</small>}
        <Button type="primary" htmlType="submit" block loading={busy} disabled={mismatch}>{submit}</Button>
        {changing?<Button type="text" disabled={busy} onClick={()=>void run(async()=>{try{await mobileCall('/api/auth/logout','POST')}finally{setPassword('');setNewPassword('');setConfirmation('');saveSession(null)}})}>退出登录</Button>:
          <Button type="link" disabled={busy} onClick={()=>{setSignup(!signup);setMsg('');setPassword('');setConfirmation('')}}>{signup?'已有账号，去登录':'没有账号？注册个人账号'}</Button>}
      </form>
    </section>;
  }
  return <section className={mobile?'mobile-booking':'pro-page'}>
    <button type="button" className="m-menu-row booking-toggle" aria-expanded={bookingOpen} onClick={()=>setBookingOpen(v=>!v)}><CalendarPlus/><span>新建预约</span><ChevronDown size={18}/></button>
    {bookingOpen&&<form onSubmit={e=>{e.preventDefault();if(busy)return;void run(async()=>{const issue=bookingIssue();if(issue)throw Error(issue);const data=await bookingSnapshot();await call<string>('appointments','POST',{doctorId:doctor,scheduledAt:new Date(time).toISOString(),snapshot:data});setBookingOpen(false);setDoctor(undefined);setTime('');setVersion(v=>v+1);setMsg('预约已提交，医生接受后即可查看资料和运行模型分析')})}}>
      <div className="booking-field"><label htmlFor="booking-doctor">预约医生</label><Select id="booking-doctor" placeholder="选择认证医生" notFoundContent="暂无可预约医生" value={doctor} options={doctors.map(d=>({value:d.id,label:d.name}))} onChange={setDoctor}/></div>
      <div className="booking-field"><label htmlFor="booking-time">预约时间</label><input id="booking-time" type="datetime-local" required value={time} onChange={e=>setTime(e.target.value)}/></div>
      <div className="booking-share"><h3>预约期间的资料使用</h3><p>提交即同意向{doctors.find(d=>d.id===doctor)?.name||'所选医生'}持续共享账号及本机贴文、本机测评，并允许对贴文进行模型分析。</p><small>医生接单后可查看。移动端在线时同步更新，每次最多 100 条贴文、100 项测评。取消或结束预约后停止访问。</small></div>
      {bookingIssue()&&<small id="booking-hint">{bookingIssue()}</small>}
      <Button size="large" type="primary" aria-describedby="booking-hint" disabled={!!bookingIssue()} loading={busy} htmlType="submit">同意并提交预约</Button></form>}{notice}
    <div className="booking-heading booking-history"><h2>预约记录</h2></div>{rows.length===0&&<p className="booking-empty">暂无预约</p>}{rows.map(r=><section className="booking-row" key={r.id}><header><h3>{r.name}</h3><Tag color={r.state==='ACCEPTED'?'green':r.state==='PENDING'?'gold':undefined}>{labels[r.state]}</Tag></header><time dateTime={r.scheduledAt}>{new Date(r.scheduledAt).toLocaleString('zh-CN',{dateStyle:'medium',timeStyle:'short'})}</time><Space wrap>
      {mobile&&['PENDING','ACCEPTED'].includes(r.state)&&<Button disabled={busy} onClick={()=>action(r.id,'cancel')}>取消预约</Button>}
      {!mobile&&r.state==='PENDING'&&<><Button disabled={busy} onClick={()=>action(r.id,'accept')}>接受预约</Button><Button disabled={busy} onClick={()=>action(r.id,'reject')}>拒绝</Button></>}
      {!mobile&&r.state==='ACCEPTED'&&<><Button disabled={busy} onClick={()=>run(async()=>{setShared(null);setShared(await call<Shared>(`appointments/${r.id}/records`))})}>查看共享资料</Button><Button disabled={busy} onClick={()=>action(r.id,'end')}>结束预约</Button></>}
    </Space></section>)}{shared&&<section><h2>本次预约共享资料</h2>{shared.posts.map((p,i)=><p key={i}>{p.date} · {p.text}</p>)}{shared.results.map((r,i)=><p key={i}>{r.date} · {r.name} · {r.score} 分</p>)}{!shared.posts.length&&!shared.results.length&&<p>用户未选择记录</p>}<Button onClick={()=>setShared(null)}>关闭资料</Button></section>}</section>;
}
