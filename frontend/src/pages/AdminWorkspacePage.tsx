import { useState } from 'react';
import { Alert, Button, Drawer, Input, Select, Space, Table, Tabs, Tag } from 'antd';
import { Link } from 'react-router-dom';

const accounts = [
  { id: 'DEMO-A01', name: '演示研究者', role: 'researcher', active: true, certification: '未申请' },
  { id: 'DEMO-A02', name: '演示医生', role: 'doctor', active: true, certification: '已通过（演示）' },
  { id: 'DEMO-A03', name: '演示用户', role: 'user', active: true, certification: '不适用' },
  { id: 'DEMO-A04', name: '演示停用账号', role: 'researcher', active: false, certification: '未申请' },
];
type Account = typeof accounts[number];
const roles: Record<string, string> = { researcher: '研究者', doctor: '医生', user: '普通用户' };
const events = [
  { id: 'DEMO-E01', time: '演示第1天', actor: '演示审核员', action: '通过医生认证', target: 'DEMO-A02', result: '授予医生身份；不授予用户资料权限' },
  { id: 'DEMO-E02', time: '演示第2天', actor: '演示数据创建者', action: '授予研究版本访问', target: '演示版本 V1', result: '只允许指定专业账号读取此版本' },
  { id: 'DEMO-E03', time: '演示第3天', actor: '演示数据创建者', action: '撤销研究版本访问', target: '演示版本 V1', result: '停止后续读取，不能收回已查看内容' },
];
export default function AdminWorkspacePage() {
  const [query, setQuery] = useState(''), [role, setRole] = useState('ALL'), [selected, setSelected] = useState<Account>();
  return <div className="pro-page">
    <div className="pro-page-heading"><div><div className="pro-eyebrow">PLATFORM ADMINISTRATION</div><h1>平台管理</h1><p className="pro-muted">集中处理认证与模型配置，查看账号及操作记录原型。</p></div><Tag>仅管理员</Tag></div>
    <div className="pro-card-grid" style={{ marginBottom: 24 }}>
      <Link className="pro-feature" to="/verification-review"><Tag>已接通</Tag><h3>医生认证审核</h3><p>查看申请材料，记录通过、驳回或撤销原因。进入后操作影响真实业务数据。</p><span className="pro-feature-action">进入审核 →</span></Link>
      <Link className="pro-feature" to="/user-management"><Tag>已接通</Tag><h3>用户档案</h3><p>搜索用户，查看资料与对应贴文。</p><span className="pro-feature-action">管理用户 →</span></Link><Link className="pro-feature" to="/model-config"><Tag>已接通</Tag><h3>模型配置</h3><p>管理已接入的模型连接和启用状态。进入后操作影响真实模型配置。</p><span className="pro-feature-action">进入配置 →</span></Link>
      <section className="pro-panel"><h3>资料访问边界</h3><p className="pro-muted">管理员身份不默认获得所有研究版本或个人贴文访问权。本页不提供用户贴文入口。</p></section>
    </div>
    <Alert type="info" showIcon message="下方账号与操作记录均为合成演示" description="未读取真实账号列表或审计日志，不支持实际角色变更、停用账号或恢复授权。上方审核和模型配置入口则为已有真实功能，请区分使用。" style={{ marginBottom: 24 }} />
    <section className="pro-panel"><Tabs items={[
      { key: 'accounts', label: '账号角色（演示）', children: <><Space wrap style={{ marginBottom: 16 }}><Input.Search aria-label="搜索管理账号" placeholder="搜索演示账号名称或编号" value={query} onChange={e => setQuery(e.target.value)} style={{ width: 300 }} /><Select aria-label="账号角色" value={role} onChange={setRole} style={{ width: 160 }} options={[{ value: 'ALL', label: '全部角色' }, ...Object.entries(roles).map(([value, label]) => ({ value, label }))]} /></Space>
        <Table<Account> rowKey="id" pagination={false} scroll={{ x: 800 }} dataSource={accounts.filter(a => (role === 'ALL' || a.role === role) && `${a.name} ${a.id}`.includes(query.trim()))} columns={[
          { title: '账号编号', dataIndex: 'id' }, { title: '名称', dataIndex: 'name' }, { title: '角色', render: (_, a) => roles[a.role] }, { title: '账号状态', render: (_, a) => a.active ? '启用' : '停用（演示）' }, { title: '医生认证', dataIndex: 'certification' }, { title: '操作', render: (_, a) => <Button onClick={() => setSelected(a)}>查看权限说明</Button> },
        ]} /></> },
      { key: 'events', label: '操作记录（演示）', children: <><p className="pro-muted">展示未来审计记录的呈现方式，不代表系统已经发生这些操作。</p><Table rowKey="id" pagination={false} scroll={{ x: 900 }} dataSource={events} columns={[{ title: '时间', dataIndex: 'time' }, { title: '操作者', dataIndex: 'actor' }, { title: '操作', dataIndex: 'action' }, { title: '对象', dataIndex: 'target' }, { title: '结果与边界', dataIndex: 'result' }]} /></> },
    ]} /></section>
    <Drawer title="账号权限说明（演示）" width={560} open={!!selected} onClose={() => setSelected(undefined)}>{selected && <Space direction="vertical" size={20}>
      <h2>{selected.name}</h2><p>角色：{roles[selected.role]} · 状态：{selected.active ? '启用' : '停用'}</p>
      <Alert type="info" message={selected.role === 'doctor' ? '医生继承全部研究功能' : selected.role === 'researcher' ? '研究者可使用研究工具并申请医生认证' : '普通用户使用个人服务，不开放研究资料'} description="任何角色均需满足具体资料的授权条件，角色身份不是用户数据授权。" />
      {!selected.active && <p>停用示例：业务访问应被阻止。此处只展示状态，不修改真实账号。</p>}
      <Button disabled>角色变更与停用操作尚未接入</Button>
    </Space>}</Drawer>
  </div>;
}




