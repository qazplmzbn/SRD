import { useEffect, useState } from 'react';
import { Alert, Button, Input, Modal, Table, Tabs, Tag, message } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { request } from '../api';
import { useAuthStore } from '../store/authStore';

type Account = { id: number; username: string; role: string; active: boolean };
const roles: Record<string, string> = { admin: '管理员', user: '个人用户', researcher: '研究者', doctor: '医生' };

export default function UserManagementPage({ group }: { group: 'mobile' | 'professional' }) {
  const mobile = group === 'mobile';
  const navigate = useNavigate();
  const currentId = useAuthStore(s => s.user?.id);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [target, setTarget] = useState<Account>();
  const [saving, setSaving] = useState(false);

  const refresh = () => setRevision(n => n + 1);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    request<{ items: Account[]; total: number }>(`/api/admin/users?group=${group}&query=${encodeURIComponent(query)}&page=${page}`)
      .then(result => { if (active) { setAccounts(result.items); setTotal(result.total); } })
      .catch(e => { if (active) { setAccounts([]); setTotal(0); setError(e.message); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [query, page, revision, group]);

  function actionLabel(account: Account) {
    if (mobile) return account.active ? '禁用账号' : '解禁账号';
    return account.role === 'admin' ? '撤销管理员权限' : '设为管理员';
  }

  async function confirmChange() {
    if (!target || saving) return;
    setSaving(true);
    try {
      await request(`/api/admin/users/${target.id}/${mobile ? 'status' : 'role'}`, {
        method: 'PUT', body: JSON.stringify(mobile ? { active: !target.active } :
          { currentRole: target.role, role: target.role === 'admin' ? 'researcher' : 'admin' }),
      });
      message.success('账号权限已更新');
      setTarget(undefined);
      refresh();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '更新失败');
    } finally {
      setSaving(false);
    }
  }

  return <div className="pro-page">
    <h1 className="sr-only">用户管理</h1>
    <Tabs activeKey={group} onChange={key => navigate(`/admin/users/${key}`)} items={[
      { key: 'professional', label: '专业用户（Web 端）' }, { key: 'mobile', label: '普通用户（移动端）' },
    ]} />
    {!mobile && <div style={{ marginBottom: 16 }}><Link to="/admin/files">审核医生认证申请</Link></div>}
    <Input.Search aria-label="搜索用户名" placeholder="搜索用户名" allowClear maxLength={100}
      onSearch={value => { setQuery(value.trim()); setPage(1); }} style={{ maxWidth: 360, marginBottom: 16 }} />
    {error && <Alert type="error" message={error} action={<Button onClick={refresh}>重试</Button>} style={{ marginBottom: 16 }} />}
    <Table<Account> rowKey="id" loading={loading} dataSource={accounts} scroll={{ x: 520 }}
      pagination={{ current: page, total, pageSize: 20, showSizeChanger: false, onChange: setPage }}
      columns={[
        { title: '用户名', dataIndex: 'username' },
        { title: '角色', dataIndex: 'role', render: role => <Tag>{roles[role] || role}</Tag> },
        { title: '账号状态', dataIndex: 'active', render: active => <Tag color={active ? 'green' : undefined}>{active ? '启用' : '停用'}</Tag> },
        { title: '操作', render: (_, account) => account.role === 'doctor' ? '由医生认证管理' : account.id === currentId ? '当前账号' :
          <Button danger={mobile && !!account.active} disabled={loading} onClick={() => setTarget(account)}>{actionLabel(account)}</Button> },
      ]} />
    <Modal open={!!target} title={target && actionLabel(target)}
      onCancel={() => { if (!saving) setTarget(undefined); }} onOk={confirmChange}
      okText="确认" cancelText="取消" confirmLoading={saving}>
      {target && <p>确认对 {target.username} 执行「{actionLabel(target)}」？</p>}
    </Modal>
  </div>;
}