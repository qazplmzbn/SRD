import { ServiceDemo } from '../components/MobileExperience';
import { useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';

type Appointment = { id: string; name: string; time: string; note: string; status: 'PENDING' | 'ACCEPTED' | 'REJECTED'; authorization: string; reason?: string };
const fixtures: Appointment[] = [
  { id: 'DEMO-001', name: '演示用户甲', time: '演示时段 A · 09:00—09:30', note: '希望讨论近期压力与睡眠变化。此内容为合成示例。', status: 'PENDING', authorization: '尚未授权资料访问' },
  { id: 'DEMO-002', name: '演示用户乙', time: '演示时段 B · 14:00—14:30', note: '希望了解自己的情绪记录。此内容为合成示例。', status: 'PENDING', authorization: '已表达授权意向，尚未生效' },
  { id: 'DEMO-003', name: '演示用户丙', time: '演示时段 C · 10:00—10:30', note: '希望进行一次沟通。此内容为合成示例。', status: 'ACCEPTED', authorization: '尚未授权资料访问' },
];
const labels = { PENDING: '待处理', ACCEPTED: '已接受', REJECTED: '已拒绝' };
export default function AppointmentsPage() {
  const role = useAuthStore(s => s.user?.role);
  const [rows, setRows] = useState<Appointment[]>(() => fixtures.map(x => ({ ...x })));
  const [query, setQuery] = useState(''), [status, setStatus] = useState('ALL'), [selected, setSelected] = useState<string>();
  const [decision, setDecision] = useState<'ACCEPTED' | 'REJECTED'>(), [reason, setReason] = useState('');
  const current = rows.find(row => row.id === selected);
  if (role !== 'doctor') return <div className="pro-page"><h1>预约管理</h1><Alert type="info" message="医生认证通过后可使用预约管理" description="研究功能仍可继续使用。" /><Link to="/certification">查看医生认证 →</Link></div>;
  const filtered = rows.filter(row => (status === 'ALL' || row.status === status) && `${row.name} ${row.id}`.includes(query.trim()));
  return <div className="pro-page">
    <ServiceDemo professional />
    <div className="pro-page-heading"><div><div className="pro-eyebrow">DOCTOR SERVICES · DEMO</div><h1>预约管理</h1><p className="pro-muted">查看预约意向，确认时段后接受，或说明无法接待的原因。</p></div><Button onClick={() => { setRows(fixtures.map(x => ({ ...x }))); setSelected(undefined); setDecision(undefined); setQuery(''); setStatus('ALL'); }}>重置演示</Button></div>
    <Alert type="info" showIcon message="原型演示 · 全部为合成数据" description="操作仅在当前页面内有效，刷新后恢复。不发送通知、不建立真实预约，也不会授予贴文或测评资料的访问权限。" style={{ marginBottom: 24 }} />
    <section className="pro-panel"><Space wrap style={{ marginBottom: 20 }}><Input.Search aria-label="搜索预约" placeholder="搜索演示用户或预约编号" value={query} onChange={e => setQuery(e.target.value)} style={{ width: 280 }} /><Select aria-label="预约状态" value={status} onChange={setStatus} style={{ width: 160 }} options={[{ value: 'ALL', label: '全部预约' }, ...Object.entries(labels).map(([value, label]) => ({ value, label }))]} /><span className="pro-muted">待处理 {rows.filter(row => row.status === 'PENDING').length} 条（演示）</span></Space>
      <Table<Appointment> rowKey="id" dataSource={filtered} pagination={{ pageSize: 10 }} locale={{ emptyText: '没有符合条件的预约' }} scroll={{ x: 900 }} columns={[
        { title: '预约编号', dataIndex: 'id' }, { title: '用户', dataIndex: 'name' }, { title: '意向时段', dataIndex: 'time' }, { title: '预约状态', render: (_, row) => <Tag>{labels[row.status]}</Tag> }, { title: '资料授权', dataIndex: 'authorization' }, { title: '操作', render: (_, row) => <Button onClick={() => setSelected(row.id)}>查看预约</Button> },
      ]} />
    </section>
    <Drawer title="预约详情（演示）" width={620} open={!!current} onClose={() => setSelected(undefined)}>
      {current && <Space direction="vertical" size={24} style={{ width: '100%' }}><Descriptions column={1} bordered items={[
        { key: 'id', label: '预约编号', children: current.id }, { key: 'name', label: '用户', children: current.name }, { key: 'time', label: '意向时段', children: current.time }, { key: 'status', label: '预约状态', children: labels[current.status] }, { key: 'note', label: '预约说明', children: current.note }, { key: 'authorization', label: '资料授权', children: current.authorization },
      ]} /><Alert type="info" message="接受预约不等于取得资料授权" description="用户贴文、测评记录等资料需要另行明确授权。本原型尚未接入授权资料查看。" />
        {current.reason && <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>处理说明：{current.reason}</p>}
        {current.status === 'PENDING' ? <Space><Button type="primary" onClick={() => { setDecision('ACCEPTED'); setReason(''); }}>接受预约</Button><Button danger onClick={() => { setDecision('REJECTED'); setReason(''); }}>拒绝预约</Button></Space> : <p className="pro-muted">此演示预约已处理，可通过“重置演示”重新体验。</p>}
      </Space>}
    </Drawer>
    <Modal title={decision === 'ACCEPTED' ? '确认接受预约（演示）' : '确认拒绝预约（演示）'} open={!!decision} onCancel={() => setDecision(undefined)} okText="确认处理" cancelText="取消" okButtonProps={{ disabled: decision === 'REJECTED' && !reason.trim(), danger: decision === 'REJECTED' }} onOk={() => {
      if (!current || current.status !== 'PENDING' || !decision || (decision === 'REJECTED' && !reason.trim())) return;
      setRows(previous => previous.map(row => row.id === current.id ? { ...row, status: decision, reason: reason.trim() } : row)); setDecision(undefined); message.success('演示状态已更新，未发送任何通知');
    }}><p>{decision === 'ACCEPTED' ? `请确认可以接待：${current?.time}。` : '请填写无法接待的原因，便于用户了解处理结果。'}</p><Input.TextArea aria-label="预约处理说明" placeholder={decision === 'ACCEPTED' ? '补充说明（选填）' : '拒绝原因（必填）'} value={reason} onChange={e => setReason(e.target.value)} maxLength={500} showCount rows={4} /></Modal>
  </div>;
}
