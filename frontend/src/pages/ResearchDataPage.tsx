import { Alert, Button, Drawer, Table, Tabs } from 'antd';
import PlatformDatasetViewer from '../components/PlatformDatasetViewer';
import { Link,useSearchParams } from 'react-router-dom';
import {useEffect,useState} from 'react';
import {request} from '../api';
import ResearchProbe from '../components/ResearchProbe';
import type {Sample} from '../components/DatasetMapping';
import {GridView,type Grid} from '../components/FreeDataset';
import './dataset-workspace.css';
import ResearchArchives from '../components/ResearchArchives';
import ResearchToolsPage from './ResearchToolsPage';
import ResearchVersionsPage from './ResearchVersionsPage';
type Version={id:string;ownerId:number;name:string;versionName:string;source:string;sampleCount:number};
function VersionDetail({id,keyword=''}:{id:string;keyword?:string}){
  const [value,setValue]=useState<Version&{samples:Sample[];table?:Grid}>(),[error,setError]=useState('');
  useEffect(()=>{const c=new AbortController();request<Version&{samples:Sample[]}>(`/api/research/versions/${encodeURIComponent(id)}`,{signal:c.signal}).then(setValue).catch(e=>{if(!c.signal.aborted)setError(e.message)});return()=>c.abort()},[id]);
  if(error)return <Alert type="error" message={error}/>;if(!value)return <p>正在读取数据集…</p>;
  if(value.table)return <section><h2>{value.name} · {value.versionName}</h2><p className="pro-muted">{value.source} · {value.table.columns.length} 列 · {value.sampleCount} 行</p><GridView grid={value.table}/></section>;
  return <section><h2>{value.name} · {value.versionName}</h2><p className="pro-muted">{value.source} · {value.sampleCount} 条贴文</p><Table size="small" scroll={{x:520}} pagination={{pageSize:10}} rowKey={(_,i)=>String(i)} dataSource={value.samples.filter(s=>s.subjectId.includes(keyword.trim())||s.text.includes(keyword.trim()))} columns={[{title:'样本编号',dataIndex:'subjectId'},{title:'贴文',dataIndex:'text',render:t=><span style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{t}</span>},{title:'标签',dataIndex:'label'},{title:'划分',dataIndex:'split'}]}/><ResearchProbe id={id} samples={value.samples}/></section>;
}

export default function ResearchDataPage() {
  const [params, setParams] = useSearchParams();
  const view = params.get('view');
  const active = view && ['archives','catalog','versions'].includes(view) ? view : 'catalog';
  const version=params.get('version'),dataset=params.get('dataset');
  const [viewer,inspect]=useState<{id:string;name:string;platform:boolean}>();
  const [rows,setRows]=useState<Version[]>([]),[error,setError]=useState('');
  useEffect(()=>{if(active==='versions')return;const c=new AbortController();setError('');setRows([]);(async()=>{const all:Version[]=[];for(let page=1;;page++){const r=await request<{items:Version[];total:number}>(`/api/research/versions?page=${page}`,{signal:c.signal});all.push(...r.items);if(!r.items.length||all.length>=r.total)break}if(!c.signal.aborted)setRows(all)})().catch(e=>{if(!c.signal.aborted)setError(e.message)});return()=>c.abort()},[active]);
  if(view==='detail'&&(version||dataset))return <section className="pro-page dataset-workspace"><Link to="/research-data?view=catalog">返回数据目录</Link>{version?<VersionDetail key={version} id={version}/>:<><h1>{dataset}</h1><PlatformDatasetViewer key={dataset} dataset={dataset!}/></>}</section>;
  return <section className="pro-page pro-research-data">
    {error&&<Alert type="error" message={error}/>}<Tabs className="dataset-workspace" activeKey={active} destroyOnHidden onChange={key => setParams({view:key})} items={[
      { key: 'versions', label: '我的评估集', children: <ResearchVersionsPage embedded /> },
      { key: 'catalog', label: '数据目录', children: <><h2>导入数据集</h2><Table size="small" rowKey="id" dataSource={rows} pagination={{pageSize:8}} columns={[{title:'数据集',render:(_,r)=><Button type="link" onClick={()=>inspect({id:r.id,name:r.name,platform:false})}>{r.name} · {r.versionName}</Button>},{title:'样本数',dataIndex:'sampleCount'}]}/><h2>平台数据集</h2><ResearchToolsPage mode="datasets" embedded onInspect={(id,name)=>inspect({id,name,platform:true})}/></> },
      { key: 'archives', label: '样本档案', children:<ResearchArchives versions={rows}/> },
    ]} /><Drawer title={viewer?.name||'数据集详情'} width="min(900px,96vw)" open={!!viewer} destroyOnHidden onClose={()=>inspect(undefined)}>{viewer&&(viewer.platform?<PlatformDatasetViewer key={viewer.id} dataset={viewer.id}/>:<VersionDetail key={viewer.id} id={viewer.id}/>)}</Drawer>
  </section>;
}