import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Space, Table } from 'antd';
import { Link } from 'react-router-dom';
import { getAuthHeader } from '../api';

type Recent = { id: string; versionName: string; modelName: string; status: string; completed: number; total: number };
const statuses: Record<string, string> = { QUEUED: '排队中', RUNNING: '运行中', SUCCEEDED: '已完成', PARTIAL_FAILED: '部分失败', FAILED: '失败', CANCELLED: '已取消' };
export default function WorkspaceActivity() {
  const [rows, setRows] = useState<Recent[]>([]), [error, setError] = useState(''), [loading, setLoading] = useState(true), [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let alive = true;
    const load = async () => {
      const raw = import.meta.env.VITE_API_BASE;
      const base = !raw || raw === 'undefined' ? '' : raw.replace(/\/api\/?$/, '').replace(/\/$/, '');
      try {
        const r = await fetch(`${base}/api/research/experiments?page=1`, { headers: getAuthHeader(), cache: 'no-store' });
        const body = await r.json();
        if (!r.ok || !body.success || !Array.isArray(body.data?.items)) throw new Error('近期实验暂时无法读取，请重试');
        if (alive) { setRows(body.data.items.slice(0, 5)); setError(''); }
      } catch (e) { if (alive) { setRows([]); setError(e instanceof Error ? e.message : '读取失败'); } }
      finally { if (alive) setLoading(false); }
    };
    setLoading(true); void load();
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load(); }, 10000);
    return () => { alive = false; window.clearInterval(timer); };
  }, [refresh]);
  return <>
    <h2 className="pro-section-title">近期实验</h2>
    <section className="pro-panel"><Space style={{ marginBottom: 16 }}><Link to="/research-experiments">进入批量实验</Link><span className="pro-muted">最近5个可访问的实验 · 实际业务数据</span></Space>
      {error ? <Alert type="error" message={error} /> : <Table<Recent> rowKey="id" loading={loading} dataSource={rows} pagination={false} locale={{ emptyText: <Empty description="还没有实验，从创建数据版本开始" /> }} scroll={{ x: 600 }} columns={[
        { title: '数据版本', dataIndex: 'versionName' }, { title: '模型', dataIndex: 'modelName' }, { title: '状态', render: (_, row) => statuses[row.status] || row.status }, { title: '处理进度', render: (_, row) => `${row.completed} / ${row.total}` },
      ]} />}
    </section>
  </>;
}
