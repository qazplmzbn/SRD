import { Button, Dropdown } from 'antd';
import { ChevronDown, UserCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { logout } from '../../api';
import { useAuthStore } from '../../store/authStore';

export default function AccountMenu({ consoleView = false }: { consoleView?: boolean }) {
  const user = useAuthStore(s => s.user);
  const clearSession = useAuthStore(s => s.logout);
  const navigate = useNavigate();
  return <Dropdown placement="bottomRight" trigger={['click']} menu={{ items: [
    { key: 'profile', label: '个人资料', onClick: () => navigate(consoleView ? '/admin/profile' : '/profile') },
    { key: 'password', label: '修改密码', onClick: () => navigate(consoleView ? '/admin/password' : '/change-password') },
    { key: 'logout', label: '退出登录', onClick: async () => { try { await logout(); } finally { clearSession(); navigate('/login'); } } },
  ] }}><Button className="pro-account" icon={<UserCircle size={18} />}>{user?.nickname || user?.username || '账号'}<ChevronDown size={14} /></Button></Dropdown>;
}