import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import MobilePrototypePage from '../src/pages/MobilePrototypePage';
import './reset.css';
import { uiTokens } from '../src/theme';

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ConfigProvider locale={zhCN} theme={{ token: uiTokens }}><MobilePrototypePage /></ConfigProvider></React.StrictMode>);
