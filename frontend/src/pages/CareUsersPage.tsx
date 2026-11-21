import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Alert, Button, Empty, Select, Space, Spin, Tabs } from 'antd';
import { RefreshCw, UserRound } from 'lucide-react';
import './care-profile.css';
import PostReview from '../components/PostReview';
import VisitBrief from '../components/VisitBrief';
import { request } from '../api';
import { useAuthStore } from '../store/authStore';

type Booking = { id: string; patientId: number; name: string; state: string; shared: boolean; scheduledAt: string };
type Records = { posts: { text: string; date?: string }[]; results: { name: string; score: number; date?: string }[] };

export default function CareUsersPage() {
  const doctorId = useAuthStore(s => s.user?.id);
  return <CareUsers key={doctorId} />;
}

function CareUsers() {
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
    <div className="pro-page-heading"><h1>用户资料</h1></div>
    {error && <Alert type="error" showIcon message={error} />}
    {loading ? <Spin /> : !error && patients.size === 0 ? <Empty description="暂无已接受且同意共享资料的预约" /> : patients.size > 0 && <>
      <div className="care-selectors">
        <Select aria-label="选择用户" placeholder="选择用户" style={{ width: 240 }} value={patientId} options={Array.from(patients, ([value, name]) => ({ value, label: `${name}（账号 ${value}）` }))} onChange={id => {
          setPatientId(id);
          const matches = rows.filter(row => row.patientId === id);
          selectBooking(matches.length === 1 ? matches[0].id : undefined);
        }} />
        <Select aria-label="选择预约" placeholder="选择预约" style={{ width: 240 }} disabled={!patientId} value={bookingId} options={bookings.map(row => ({ value: row.id, label: `${new Date(row.scheduledAt).toLocaleString()} · ${row.id.slice(0, 8)}` }))} onChange={selectBooking} />
      </div>
      {patientId!==undefined?<><header className="care-identity"><div className="care-initial" aria-hidden="true">{Array.from(patients.get(patientId)||'')[0]||<UserRound/>}</div><div><h2>{patients.get(patientId)}</h2><p>账号 {patientId}</p></div><dl><div><dt>授权预约</dt><dd>{bookings.length} 次</dd></div><div><dt>当前预约时间</dt><dd>{bookingId?new Date(bookings.find(r=>r.id===bookingId)!.scheduledAt).toLocaleString():'尚未选择'}</dd></div></dl></header>
      <section className="care-records"><h2>本次共享记录</h2>{recordError ? <Alert type="error" showIcon message={recordError} /> : records ? <Tabs key={bookingId} items={[
        {key:'brief',label:'诊前概览',children:<VisitBrief key={JSON.stringify(records.posts)} {...records}/>},
        { key: 'posts', label: `贴文 (${records.posts.length})`, children: records.posts.length ? records.posts.map((post, i) => <article className="pro-review-item" key={i}><div><small className="pro-muted">{post.date}</small><p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{post.text}</p></div></article>) : <Empty description="本次预约没有共享贴文" /> },
        { key: 'scales', label: `量表结果 (${records.results.length})`, children: records.results.length ? records.results.map((result, i) => <article className="pro-review-item" key={i}><div><strong>{result.name}</strong><p className="pro-muted">{result.date}</p></div><span>{result.score} 分</span></article>) : <Empty description="本次预约没有共享量表结果" /> },
        {key:'analysis',label:'模型复核',children:<PostReview key={JSON.stringify(records.posts)} id={bookingId!} posts={records.posts}/>},
      ]} /> : bookingId ? <Spin /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择一次预约"/>}</section></>:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择用户"/>}
    </>}
  </section>;
}