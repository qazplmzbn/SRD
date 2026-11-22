import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Alert, Collapse, Empty, Select, Spin, Tabs } from 'antd';
import './care-profile.css';
import DoctorRiskDesk from '../components/DoctorRiskDesk';
import VisitBrief from '../components/VisitBrief';
import SharedPosts from '../components/SharedPosts';
import { request } from '../api';
import { useAuthStore } from '../store/authStore';

type Booking = { id: string; patientId: number; name: string; state: string; shared: boolean; scheduledAt: string };
type Records = { posts: { text: string; date?: string }[]; results: { name: string; score: number; date?: string }[] };

export default function CareUsersPage({view='patients'}:{view?:string}) {
  const doctorId = useAuthStore(s => s.user?.id);
  return <CareUsers key={doctorId} view={view} />;
}

function CareUsers({view}:{view:string}) {
  const initialBooking=useRef(useLocation().state?.appointment);
  const [rows, setRows] = useState<Booking[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [bookingId, setBookingId] = useState<string>();
  const [records, setRecords] = useState<Records>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [recordError, setRecordError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    request<Booking[]>('/api/care/appointments', { signal: controller.signal, cache: 'no-store' })
      .then(data => { if (controller.signal.aborted)return; const allowed=data.filter(row => row.state === 'ACCEPTED' && row.shared && Number.isSafeInteger(row.patientId));setRows(allowed);const selected=allowed.find(r=>r.id===initialBooking.current);initialBooking.current=undefined;if(selected){setPatientId(selected.patientId);setBookingId(selected.id)} })
      .catch(e => { if (!controller.signal.aborted) { setRows([]); setError(e.message); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);

  useEffect(() => {
    if (!bookingId) return;
    const controller = new AbortController();
    let timer:ReturnType<typeof setTimeout>;
    const load=()=>request<Records>(`/api/care/appointments/${encodeURIComponent(bookingId)}/records`, { signal: controller.signal, cache: 'no-store' })
      .then(data => { if (!controller.signal.aborted){setRecords(data);setRecordError('')} })
      .catch(e => { if (!controller.signal.aborted){setRecords(undefined);setRecordError(e.message)} })
      .finally(()=>{if(!controller.signal.aborted)timer=setTimeout(load,5000)});
    void load();return () => {controller.abort();clearTimeout(timer)};
  }, [bookingId]);

  const patients = new Map(rows.map(row => [row.patientId, row.name]));
  const bookings = rows.filter(row => row.patientId === patientId);
  const selectBooking = (id?: string) => { setRecords(undefined); setRecordError(''); setBookingId(id); };
  return <section className="pro-page">
    {view==='patients'?<>
    {error && <Alert type="error" showIcon message={error} />}
    {loading ? <Spin /> : !error && patients.size === 0 ? <Empty description="暂无已接受的预约" /> : patients.size > 0 && <>
      <dl className="care-stats" aria-label="共享资料统计"><div><dt>共享用户</dt><dd>{patients.size} 人</dd></div><div><dt>共享预约</dt><dd>{rows.length} 次</dd></div><div><dt>多次预约用户</dt><dd>{[...patients.keys()].filter(id=>rows.filter(b=>b.patientId===id).length>1).length} 人</dd></div></dl>
      <Collapse accordion activeKey={patientId===undefined?[]:[String(patientId)]} onChange={key=>{const raw=Array.isArray(key)?key[0]:key;const id=raw?Number(raw):undefined;setPatientId(id);selectBooking(rows.find(row=>row.patientId===id)?.id)}} items={Array.from(patients,([id,name])=>({key:String(id),label:`${name}（账号 ${id}）`,extra:`${rows.filter(b=>b.patientId===id).length} 次共享预约`,children:patientId===id&&<><div className="care-selectors">
        <Select aria-label="选择预约" placeholder="选择预约" style={{ width: 240 }} disabled={!patientId} value={bookingId} options={bookings.map(row => ({ value: row.id, label: `${new Date(row.scheduledAt).toLocaleString()} · ${row.id.slice(0, 8)}` }))} onChange={selectBooking} />
      </div>
      <section className="care-records"><h2>本次共享记录</h2>{recordError ? <Alert type="error" showIcon message={recordError} /> : records ? <Tabs key={bookingId} items={[
        {key:'brief',label:'诊前概览',children:<><header className="care-identity"><span className="care-initial">{name.slice(0,1)}</span><div><h2>{name}</h2><p>账号 {id}</p></div><dl><div><dt>预约时间</dt><dd>{new Date(bookings.find(b=>b.id===bookingId)!.scheduledAt).toLocaleString()}</dd></div><div><dt>模型分析</dt><dd>{records.posts.length?'可分析':'无共享贴文'}</dd></div></dl></header><VisitBrief key={JSON.stringify(records.posts)} {...records}/></>},
        { key: 'posts', label: `贴文 (${records.posts.length})`, children:<SharedPosts posts={records.posts}/> },
        { key: 'scales', label: `量表结果 (${records.results.length})`, children: records.results.length ? records.results.map((result, i) => <article className="pro-review-item" key={i}><div><strong>{result.name}</strong><p className="pro-muted">{result.date}</p></div><span>{result.score} 分</span></article>) : <Empty description="本次预约没有共享量表结果" /> },
      ]} /> : bookingId ? <Spin /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择一次预约"/>}</section></>}))}/>
    </>}
      </>:<DoctorRiskDesk/>}
  </section>;
}