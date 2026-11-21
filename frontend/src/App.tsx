import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import UserManagementPage from './pages/UserManagementPage';

import Layout from './components/layout/Layout';
import WorkspaceHomePage from './pages/WorkspaceHomePage';
import { canOpenSection } from './access';
import { useLocation } from 'react-router-dom';
import ModelCenterPage from './pages/ModelCenterPage';
import PersonalModelSettings from './pages/PersonalModelSettings';
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
              <Route path="research-experiments" element={<Navigate to="/research-data?view=experiments" replace />} />
            <Route path="model-analysis" element={<RiskPage />} />
            <Route path="research-models" element={<Navigate to="/model" replace />} />
            <Route path="certification" element={<DoctorInvite />} />
            <Route path="appointments" element={<CareWorkspace />} />
            <Route path="admin-workspace" element={<AdminWorkspacePage />} />
            <Route path="authorized-users" element={<Navigate to="/care-users" replace />} />
            <Route path="care-users" element={<CareUsersPage />} />
            <Route path="verification-review" element={<DoctorVerificationReviewPage />} />
            <Route path="model" element={<PersonalModelSettings />} />
            <Route path="model-config" element={<ModelCenterPage />} />
            <Route path="model-config/template" element={<ModelCenterPage />} />
            <Route path="model/template" element={<PersonalModelSettings />} />
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




