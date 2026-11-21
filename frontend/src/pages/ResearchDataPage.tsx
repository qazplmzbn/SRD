import { Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';
import ArchivePage from './ArchivePage';
import ResearchToolsPage from './ResearchToolsPage';
import ResearchVersionsPage from './ResearchVersionsPage';
import ResearchExperimentsPage from './ResearchExperimentsPage';

export default function ResearchDataPage() {
  const [params, setParams] = useSearchParams();
  const view = params.get('view');
  const active = view && ['archives','catalog','experiments','versions'].includes(view) ? view : 'versions';
  return <section className="pro-page pro-research-data">
    <div className="pro-page-heading"><h1>研究数据</h1></div>
    <Tabs activeKey={active} destroyOnHidden onChange={key => setParams({view:key})} items={[
      { key: 'versions', label: '我的评估集', children: <ResearchVersionsPage embedded /> },
      { key: 'experiments', label: '实验与误判复核', children: <ResearchExperimentsPage /> },
      { key: 'catalog', label: '数据目录', children: <ResearchToolsPage mode="datasets" embedded /> },
      { key: 'archives', label: '样本档案', children: <ArchivePage key={params.get('dataset') ?? ''} dataset={params.get('dataset') ?? ''} /> },
    ]} />
  </section>;
}