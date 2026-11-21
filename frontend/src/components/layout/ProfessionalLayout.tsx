
import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Dropdown, Button } from 'antd';
import { ChevronDown, Layers3 } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { logout } from '../../api';
import { canOpenSection } from '../../access';
import './professional.css';

const groups = [
  { label: '研究工作台', items: [
    { path: '/research-data', label: '研究数据', related: ['/archive'] },

    { path: '/model-analysis', label: '风险检测', related: ['/risk'] },
    { path: '/model', label: '模型配置' },
    { path: '/model/template', label: '提示词配置' },
  ] },
  { label: '专业服务', items: [
    { path: '/certification', label: '医生认证' },
    { path: '/appointments', label: '预约管理', related: ['/authorized-users'] },
    { path: '/care-users', label: '用户资料', related: ['/scale'] },
  ] },
  { label: '平台管理', items: [
    { path: '/admin-workspace', label: '管理概览' },
    { path: '/user-management', label: '用户档案' },
    { path: '/verification-review', label: '认证审核' },
  ] },
];

function matchesRoute(pathname: string, path: string) {
  return pathname === path || pathname.startsWith(`${path}/`);
}

export default function ProfessionalLayout() {
  const user = useAuthStore(s => s.user);

  const clearSession = useAuthStore(s => s.logout);
  const navigate = useNavigate();
  const { pathname, key } = useLocation();
  const [menu, setMenu] = useState<{ label: string; locationKey: string }>();
  const openMenu = menu?.locationKey === key ? menu?.label : undefined;

  function renderGroup({ label, items }: typeof groups[number]) {
    const visible = items.filter(item => canOpenSection(item.path.slice(1), user?.role) && !(item.path === '/certification' && user?.role === 'doctor'));
    if (!visible.length) return null;
    const active = visible.find(item => item.path===pathname) ?? visible.find(item => [item.path, ...(item.related ?? [])].some(path => matchesRoute(pathname, path)));
    return <Dropdown key={label} trigger={['click']} placement="bottomLeft" autoFocus
      open={openMenu === label} onOpenChange={open => setMenu(current => open ? { label, locationKey: key } : current?.label === label ? undefined : current)}
      menu={{ selectable: true, selectedKeys: active ? [active.path] : [], onClick: ({ key }) => { setMenu(undefined); navigate(key); }, items: visible.map(item => ({
        key: item.path, label: item.label, 'aria-current': active?.path === item.path ? 'page' : undefined,
      })) }}>
      <button type="button" className={`pro-nav-item${active ? ' active' : ''}`} aria-haspopup="menu" aria-expanded={openMenu === label}>
        {label}<ChevronDown size={14} aria-hidden="true" />
      </button>
    </Dropdown>;
  }
  return <div className="pro-shell">
    <header className="pro-header">
      <Link to="/home" className="pro-brand"><span className="pro-mark"><Layers3 size={21} /></span><strong>VIS4SRD</strong><span className="pro-brand-caption">专业平台</span></Link>
      <nav aria-label="专业主导航" className="pro-primary-nav">
        <NavLink className="pro-nav-item" to="/home">首页</NavLink>
        {renderGroup(groups[0])}
        <NavLink className="pro-nav-item" to="/chat">智能问答</NavLink>
        <NavLink className="pro-nav-item" to="/knowledge">知识库</NavLink>
        <NavLink className="pro-nav-item" to="/map">心理援助地图</NavLink>
        {groups.slice(1).map(renderGroup)}
      </nav>
      <Dropdown menu={{ items: [
        { key: 'profile', label: '个人资料', onClick: () => navigate('/profile') },
        { key: 'password', label: '修改密码', onClick: () => navigate('/change-password') },
        { key: 'logout', label: '退出登录', onClick: async () => { try { await logout(); } finally { clearSession(); navigate('/login'); } } },
      ] }} trigger={['click']}><Button className="pro-account">{user?.nickname || user?.username || '账号'}<ChevronDown size={14} /></Button></Dropdown>
    </header>
    <main className={`pro-content ${['/chat', '/map'].includes(pathname) ? 'pro-content-contained' : ''}`}><Outlet /></main>
  </div>;
}







