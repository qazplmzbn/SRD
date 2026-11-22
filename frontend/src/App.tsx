import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Button,ConfigProvider,Dropdown } from 'antd';
import {CalendarDays,ChevronDown,Users} from 'lucide-react';
import zhCN from 'antd/locale/zh_CN';
import UserManagementPage from './pages/UserManagementPage';

import Layout from './components/layout/Layout';
import WorkspaceHomePage from './pages/WorkspaceHomePage';
import { canOpenSection } from './access';
import { useLocation, useNavigate } from 'react-router-dom';
import ModelCenterPage from './pages/ModelCenterPage';
import KnowledgeBasePage from './pages/KnowledgeBasePage';
import KnowledgeDocDetailPage from './pages/KnowledgeDocDetailPage';
import ChatPage from './pages/ChatPage';
import DocPreviewPage from './pages/DocPreviewPage';
import ArchiveDetailPage from './pages/ArchiveDetailPage';
import RiskPage from './pages/RiskPage';
import MapPage from './pages/MapPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProfilePage from './pages/ProfilePage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import PersonalPostsPage from './pages/PersonalPostsPage';
import CareWorkspace, {DoctorInvite} from './components/CareWorkspace';
import MobilePrototypePage from './pages/MobilePrototypePage';
import AdminWorkspacePage from './pages/AdminWorkspacePage';
import DoctorVerificationReviewPage from './pages/DoctorVerificationReviewPage';
import ResearchDataPage from './pages/ResearchDataPage';
import CareUsersPage from './pages/CareUsersPage';
import { useAuthStore } from './store/authStore';
import { ReactNode } from 'react';

/** 路由守卫：未登录用户重定向到登录页 */
function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuthStore();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  const section = location.pathname.split('/')[1].replace('model-center', 'model');
  if (!canOpenSection(section, user?.role)) return <Navigate to="/home" replace />;
  return <>{children}</>;
}

/** 路由守卫：已登录用户重定向到首页（防止重复登录） */
function GuestGuard({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuthStore();
  if (isAuthenticated) {
    return <Navigate to="/home" replace />;
  }
  return <>{children}</>;
}

function DoctorCareRoute() {
  const { pathname,search,state } = useLocation();
  const navigate = useNavigate();
  const groups=[{path:'/appointments',title:'预约管理',icon:<CalendarDays size={16}/>,items:[['ALL','全部预约'],['PENDING','待接受'],['ACCEPTED','已接受'],['CLOSED','已关闭']]},{path:'/care-users',title:'用户资料',icon:<Users size={16}/>,items:[['patients','资料查看'],['analysis','模型分析']]}];
  const current=groups.find(g=>g.path===pathname)??groups[0];
  const item=current.items.find(([key])=>key===new URLSearchParams(search).get('view'))??current.items[0];
  return <><nav aria-label="专业服务导航" style={{display:'flex',gap:8,flexWrap:'wrap',marginBottom:16}}>{groups.map(g=><Dropdown key={g.path} trigger={['click']} menu={{selectable:true,selectedKeys:g===current?[item[0]]:[],items:g.items.map(([key,label])=>({key,label})),onClick:({key})=>navigate({pathname:g.path,search:key===g.items[0][0]?'':`?view=${key}`},{state:g===current?state:null})}}><Button type={g===current?'primary':'default'} icon={g.icon}>{g.title}{g===current&&item!==g.items[0]?` · ${item[1]}`:''}<ChevronDown size={14}/></Button></Dropdown>)}</nav>{pathname==='/appointments'?<CareWorkspace filter={item[0]}/>:<CareUsersPage view={item[0]}/>}</>;
}

function PersonalPostsRoute() {
  const role = useAuthStore(s => s.user?.role);
  return role === 'admin' ? <Navigate to="/user-management" replace /> : <PersonalPostsPage />;
}

import { uiTokens } from './theme';

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          ...uiTokens,
          colorSuccess: '#1F9D72',
          colorWarning: '#F59E0B',
          colorError: '#DC2626',
          boxShadowSecondary: '0 18px 40px rgba(15, 23, 42, 0.08)',
        },
      }}
    >
      <BrowserRouter>
        <Routes>
          <Route path="/mobile/*" element={<MobilePrototypePage />} />

          {/* ========== 公开路由（无需登录） ========== */}
          <Route
            path="/login"
            element={
              <GuestGuard>
                <LoginPage />
              </GuestGuard>
            }
          />
          <Route path="/register" element={<GuestGuard><RegisterPage /></GuestGuard>} />

          {/* ========== 受保护路由（需要登录） ========== */}
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route path="/" element={<Navigate to="/home" replace />} />
            <Route path="home" element={<WorkspaceHomePage />} />
            <Route path="my-posts" element={<PersonalPostsRoute />} />
            <Route path="user-management" element={<UserManagementPage />} />
            <Route path="support-resources" element={<Navigate to="/model-analysis" replace />} />
            <Route path="research-data" element={<ResearchDataPage />} />
            <Route path="research-versions" element={<Navigate to="/research-data" replace />} />
            <Route path="research-experiments" element={<Navigate to="/research-data?view=catalog" replace />} />
            <Route path="model-analysis" element={<RiskPage />} />
            <Route path="research-models" element={<Navigate to="/model" replace />} />
            <Route path="certification" element={<DoctorInvite />} />
            <Route path="appointments" element={<DoctorCareRoute />} />
            <Route path="admin-workspace" element={<AdminWorkspacePage />} />
            <Route path="authorized-users" element={<Navigate to="/care-users" replace />} />
            <Route path="care-users" element={<DoctorCareRoute />} />
            <Route path="verification-review" element={<DoctorVerificationReviewPage />} />
            <Route path="model" element={<ModelCenterPage />} />
            <Route path="model-config" element={<Navigate replace to="/model" />} />
            <Route path="model-config/template" element={<Navigate replace to="/model/template" />} />
            <Route path="model/template" element={<ModelCenterPage />} />
            <Route path="model-center" element={<Navigate to="/model" replace />} />
            <Route path="model-center/template" element={<Navigate to="/model/template" replace />} />
            <Route path="knowledge" element={<KnowledgeBasePage />} />
            <Route path="knowledge/detail" element={<KnowledgeDocDetailPage />} />
            <Route path="chat" element={<ChatPage />} />
            <Route path="chat/doc-preview" element={<DocPreviewPage />} />
            <Route path="doc-preview" element={<DocPreviewPage />} />
            <Route path="archive" element={<Navigate to="/research-data?view=archives" replace />} />
            <Route path="archive/detail/:archiveId" element={<ArchiveDetailPage />} />
            <Route path="scale/*" element={<Navigate to="/care-users" replace />} />
            <Route path="risk" element={<Navigate to="/model-analysis" replace />} />
            <Route path="map" element={<MapPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="change-password" element={<ChangePasswordPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}

export default App;




