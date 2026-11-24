import { useEffect, useState } from 'react';
import { Alert, Button, Input, Modal, Radio, Table, Tag, message } from 'antd';
import { request } from '../api';

type Appointment = { id: string; state: string; patient: string; doctor: string; scheduledAt: string; opinion?: string; decision?: 'APPROVED' | 'REJECTED' };
type Results = { items: Appointment[]; total: number };
const states: Record<string, string> = { PENDING: '待接诊', ACCEPTED: '已接受', REJECTED: '已拒绝', CANCELLED: '已取消', ENDED: '已结束' };

export default function AdminAppointmentsPage() {
  const [page, setPage] = useState(1), [revision, setRevision] = useState(0);
  const [results, setResults] = useState<Results>(), [error, setError] = useState('');
  const [selected, setSelected] = useState<Appointment>(), [opinion, setOpinion] = useState(''), [saving, setSaving] = useState(false);
  const [decision, setDecision] = useState<Appointment['decision']>();
  useEffect(() => {
    let active = true;
    setResults(undefined); setError('');
    request<Results>(`/api/admin/appointments?page=${page}`)
      .then(data => { if (active) setResults(data); })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [page, revision]);

  async function save() {
    if (!selected || !decision || !opinion.trim() || saving) return;
    setSaving(true);
    try {
      await request(`/api/admin/appointments/${encodeURIComponent(selected.id)}/review`, {
        method: 'POST', body: JSON.stringify({ decision, opinion: opinion.trim() }),
      });
      message.success('预约审批已提交'); setSelected(undefined); setRevision(n => n + 1);
    } catch (e) { message.error(e instanceof Error ? e.message : '保存失败'); }
    finally { setSaving(false); }
  }

  return <div className="pro-page"><h1 className="sr-only">预约审批</h1>
    {error && <Alert type="error" message={error} action={<Button onClick={() => setRevision(n => n + 1)}>重试</Button>} />}
    <Table<Appointment> rowKey="id" loading={!results && !error} dataSource={results?.items} scroll={{ x: 800 }}
      pagination={{ current: page, total: results?.total, pageSize: 20, showSizeChanger: false, onChange: setPage }}
      columns={[
        { title: '患者', dataIndex: 'patient' }, { title: '医生', dataIndex: 'doctor' },
        { title: '预约时间', render: (_, row) => new Date(row.scheduledAt).toLocaleString() },
        { title: '状态', render: (_, row) => row.state === 'PENDING' && !row.decision ? <Tag color="gold">待管理员审批</Tag> : <Tag>{states[row.state] || row.state}</Tag> },
        { title: '审批记录', render: (_, row) => <>{row.decision && <Tag color={row.decision==='APPROVED'?'green':'red'}>{row.decision==='APPROVED'?'通过':'驳回'}</Tag>}{row.opinion || '—'}</> },
        { title: '操作', render: (_, row) => row.state==='PENDING' && !row.decision ? <Button type="primary" onClick={() => { setSelected(row); setOpinion(''); setDecision(undefined); }}>审批</Button> : '—' },
      ]} />
    <Modal title="预约审批" open={!!selected} onOk={save} confirmLoading={saving} okText="提交审批"
      okButtonProps={{ disabled: !decision || !opinion.trim() }} onCancel={() => { if (!saving) setSelected(undefined); }}>
      <Radio.Group value={decision} onChange={e=>setDecision(e.target.value)} options={[{value:'APPROVED',label:'通过'},{value:'REJECTED',label:'驳回'}]} />
      <p>预约编号 {selected?.id}</p><Input.TextArea aria-label="处理意见" rows={4} maxLength={1000} showCount value={opinion} onChange={e => setOpinion(e.target.value)} />
    </Modal>
  </div>;
}