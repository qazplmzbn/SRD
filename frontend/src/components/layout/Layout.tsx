import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Alert, Button, Spin } from 'antd';
import ProfessionalLayout from './ProfessionalLayout';
import { useAuthStore } from '../../store/authStore';
import { canUseResearch } from '../../access';
import { fetchMe } from '../../api';
import './professional.css';

export default function Layout() {
  const role = useAuthStore(s => s.user?.role);
  const updateUser = useAuthStore(s => s.updateUser);
  const clearSession = useAuthStore(s => s.logout);
  const location = useLocation();
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    const refresh = () => {
      if (document.visibilityState === 'hidden') return;
      fetchMe().then(user => {
        if (active) { updateUser(user); setReady(true); setError(''); }
      }).catch(e => { if (active) setError(e instanceof Error ? e.message : '无法读取账号角色'); });
    };
    refresh();
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    return () => { active = false; window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh); };
  }, [location.pathname, updateUser, retry]);
  if (!ready) return <div className="p-8">{error ? <Alert type="error" message={error} action={<Button onClick={() => setRetry(v => v + 1)}>重试</Button>} /> : <Spin tip="正在读取账号权限"><div style={{height:100}} /></Spin>}</div>;
  if (canUseResearch(role)) return <ProfessionalLayout />;
  return <div className="p-8"><Alert type="warning" showIcon message="此账号没有专业工作台访问权限" description="个人账号请使用移动端。" action={<Button onClick={clearSession}>切换账号</Button>} /></div>;
}





