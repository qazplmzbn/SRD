import { useState } from 'react';
import { Dropdown } from 'antd';
import { ChevronDown, ShieldCheck } from 'lucide-react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import AccountMenu from './AccountMenu';
import './professional.css';

const managementItems = [
  { key: '/admin/appointments', label: '预约审批' },
  { key: '/admin/users', label: '用户管理' },
  { key: '/admin/files', label: '医生认证' },
];

export default function AdminLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [managementOpen, setManagementOpen] = useState(false);
  const active = managementItems.find(item => item.key === pathname || pathname.startsWith(`${item.key}/`));

  return <div className="pro-shell admin-console">
    <header className="pro-header">
      <div className="pro-brand"><span className="pro-mark"><ShieldCheck size={21} /></span><strong>VIS4SRD</strong><span className="pro-brand-caption">管理后台</span></div>
      <nav aria-label="管理员主导航" className="pro-primary-nav">
        <Dropdown trigger={['click']} placement="bottomLeft" autoFocus open={managementOpen} onOpenChange={setManagementOpen}
          menu={{ selectable: true, selectedKeys: active ? [active.key] : [], items: managementItems,
            onClick: ({ key }) => { setManagementOpen(false); navigate(key); } }}>
          <button type="button" className={`pro-nav-item${active ? ' active' : ''}`} aria-haspopup="menu" aria-expanded={managementOpen}>
            系统管理<ChevronDown size={14} aria-hidden="true" />
          </button>
        </Dropdown>
        <NavLink className="pro-nav-item" to="/admin/config">系统配置</NavLink>
        <NavLink className="pro-nav-item" to="/home">查看系统</NavLink>
      </nav>
      <AccountMenu consoleView />
    </header>
    <main className="pro-content"><Outlet /></main>
  </div>;
}