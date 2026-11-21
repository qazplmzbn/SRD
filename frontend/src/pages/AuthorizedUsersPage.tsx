import { ServiceDemo } from '../components/MobileExperience';
import { useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Input, Select, Space, Table, Tag } from 'antd';
import DemoUserDetail from '../components/DemoUserDetail';

type UserRecord = { id: string; name: string; status: 'ACTIVE' | 'NONE' | 'EXPIRED' | 'REVOKED'; scope: string; period: string; updated: string; todo: string };
const labels = { ACTIVE: '授权有效', NONE: '尚未授权', EXPIRED: '授权已到期', REVOKED: '授权已撤销' };
const records: UserRecord[] = [
  { id: 'DEMO-U01', name: '演示用户甲', status: 'ACTIVE', scope: '已导入贴文、心理测评记录', period: '演示第1天至第30天（有效期内）', updated: '演示第12天', todo: '有新的贴文待查看' },
  { id: 'DEMO-U02', name: '演示用户乙', status: 'NONE', scope: '无', period: '未建立授权', updated: '—', todo: '等待用户决定是否授权' },
  { id: 'DEMO-U03', name: '演示用户丙', status: 'EXPIRED', scope: '历史授权：已导入贴文', period: '演示第1天至第10天（已到期）', updated: '不可读取', todo: '资料访问已停止' },
  { id: 'DEMO-U04', name: '演示用户丁', status: 'REVOKED', scope: '历史授权：心理测评记录', period: '演示第8天撤销', updated: '不可读取', todo: '资料访问已停止' },
];
export default function AuthorizedUsersPage() {
  const [detailId, setDetailId] = useState<string>();
  const [query, setQuery] = useState(''), [status, setStatus] = useState('ALL'), [selected, setSelected] = useState<UserRecord>();
  const filtered = records.filter(row => (status === 'ALL' || row.status === status) && `${row.name} ${row.id}`.includes(query.trim()));
  return <div className="pro-page">
    <ServiceDemo professional />
    <div className="pro-page-heading"><div><div className="pro-eyebrow">AUTHORIZED USERS · DEMO</div><h1>授权用户</h1><p className="pro-muted">查看服务用户的授权范围，确认哪些资料可以访问。</p></div></div>
    <Alert showIcon type="info" message="原型演示 · 全部为合成记录" description="使用相对演示日期呈现不同状态，尚未接入真实授权及到期判断。医生身份或已接受预约均不代表获得资料访问权。" style={{ marginBottom: 24 }} />
    <section className="pro-panel"><Space wrap style={{ marginBottom: 20 }}><Input.Search aria-label="搜索授权用户" placeholder="搜索演示用户或编号" value={query} onChange={e => setQuery(e.target.value)} style={{ width: 280 }} /><Select aria-label="授权状态" value={status} onChange={setStatus} style={{ width: 180 }} options={[{ value: 'ALL', label: '全部授权状态' }, ...Object.entries(labels).map(([value, label]) => ({ value, label }))]} /></Space>
      <Table<UserRecord> rowKey="id" dataSource={filtered} pagination={{ pageSize: 10 }} scroll={{ x: 1100 }} locale={{ emptyText: '没有符合条件的用户' }} columns={[
        { title: '用户', render: (_, row) => <><strong>{row.name}</strong><p className="pro-muted">{row.id}</p></> },
        { title: '授权状态', render: (_, row) => <Tag>{labels[row.status]}</Tag> }, { title: '授权范围', dataIndex: 'scope' }, { title: '有效期（演示）', dataIndex: 'period' }, { title: '资料更新', dataIndex: 'updated' }, { title: '待办提示', dataIndex: 'todo' },
        { title: '操作', render: (_, row) => <Space direction="vertical"><Button onClick={() => setSelected(row)}>查看授权说明</Button><Button disabled={row.status !== 'ACTIVE'} title={row.status === 'ACTIVE' ? '查看合成演示资料' : '当前无有效资料访问授权'} onClick={() => { if (row.status === 'ACTIVE') setDetailId(row.id); }}>查看用户资料</Button></Space> },
      ]} />
    </section>
    {detailId && records.some(row => row.id === detailId && row.status === 'ACTIVE') && <DemoUserDetail onClose={() => setDetailId(undefined)} />}
    <Drawer title="授权说明（演示）" width={600} open={!!selected} onClose={() => setSelected(undefined)}>{selected && <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <Descriptions column={1} bordered items={[{ key: 'name', label: '用户', children: selected.name }, { key: 'state', label: '授权状态', children: labels[selected.status] }, { key: 'scope', label: '范围', children: selected.scope }, { key: 'period', label: '有效期', children: selected.period }]} />
      <Alert type={selected.status === 'ACTIVE' ? 'info' : 'warning'} message={selected.status === 'ACTIVE' ? '仅在明确授权的范围内查看' : '当前不能读取用户资料'} description={selected.status === 'ACTIVE' ? '可关闭此说明，在列表点击“查看用户资料”体验合成详情。此处没有真实授权或用户资料。' : '过期、撤销或未授权时，不能访问贴文、测评和变化分析。医生不能自行恢复用户授权。'} />
    </Space>}</Drawer>
  </div>;
}
