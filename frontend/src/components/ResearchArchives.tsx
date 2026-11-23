import {useEffect,useState} from 'react';
import {Alert,Button,Modal,Select,Space,Table} from 'antd';
import {FolderPlus} from 'lucide-react';
import {request} from '../api';
import {readCloud} from '../researchCloud';
import {useAuthStore} from '../store/authStore';
import ArchivePage from '../pages/ArchivePage';
import type {Grid} from './FreeDataset';

type Source={kind:'platform'|'version';id:string;name:string};
type Platform={datasetKey:string;displayName:string;isBuiltin:boolean;isActive:boolean};
const key=(s:Source)=>`${s.kind}/${encodeURIComponent(s.id)}`;
function PersonalArchives({id}:{id:string}) {
  type Group={subject:string;posts:{text:string;label:string|number}[]};
  const [groups,setGroups]=useState<Group[]>(),[error,setError]=useState('');
  const [family,setFamily]=useState('emocc'),[running,setRunning]=useState(false);
  const [report,setReport]=useState<{subject:string;riskLevel:string;summary:string}>();
  function analyze(subject:string){
    const cloud=readCloud(Number(useAuthStore.getState().user?.id));
    if(!cloud){setError('请先在个人模型配置中应用自有档案报告模型、API Key 和提示词');return}
    Modal.confirm({title:`分析 ${subject} 的贴文？`,content:`最多 32 条贴文将发送至 ${cloud.provider} (${cloud.model})。${family==='emocc'?'Emocc 还会将原文发送至部署配置的表情生成服务。':''}请确认已获得授权。`,okText:'同意并分析',onOk:async()=>{setRunning(true);setReport(undefined);setError('');try{const result=await request<{riskLevel:string;summary:string}>(`/api/research/archives/version/${encodeURIComponent(id)}/review`,{method:'POST',body:JSON.stringify({subject,family,cloud,consent:true})});setReport({subject,...result})}catch(e){setError((e as Error).message)}finally{setRunning(false)}}})}
  useEffect(()=>{const c=new AbortController();request<Group[]>(`/api/research/archives/version/${encodeURIComponent(id)}`,{signal:c.signal}).then(setGroups).catch(e=>{if(!c.signal.aborted)setError(e.message)});return()=>c.abort()},[id]);
  return <><Select aria-label="分析模型" value={family} onChange={setFamily} options={[{value:'api',label:'大模型'},{value:'emocc',label:'Emocc-Weibo'},{value:'fealearner',label:'FeaLearner-Weibo'}]}/>{error&&<Alert type="error" message={error}/>} {report&&<Alert message={`${report.subject} · ${report.riskLevel}`} description={report.summary}/>}<Table rowKey="subject" loading={!groups&&!error} dataSource={groups} columns={[{title:'样本账号',dataIndex:'subject'},{title:'贴文数',render:(_,r)=>r.posts.length},{title:'检测报告',render:(_,r)=><Button disabled={running} onClick={()=>analyze(r.subject)}>生成报告</Button>}]} expandable={{expandedRowRender:r=><ol>{r.posts.map((p,i)=><li key={i} style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{p.text} · 标签 {p.label}</li>)}</ol>}}/></>;
}
export default function ResearchArchives({versions}:{versions:{id:string;name:string;versionName:string;ownerId:number}[]}) {
  const owner=useAuthStore(s=>s.user?.id);
  const canManage=useAuthStore(s=>s.user?.role==='admin');
  const [importing,setImporting]=useState(false);
  const [rows,setRows]=useState<Source[]>([]),[platform,setPlatform]=useState<Platform[]>([]),[selection,select]=useState<string>(),[open,inspect]=useState<Source>();
  const [busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState('');
  useEffect(()=>{const c=new AbortController();Promise.all([request<Source[]>('/api/research/archives',{signal:c.signal}),request<Platform[]>('/api/datasets',{signal:c.signal})]).then(([r,p])=>{setRows(r);setPlatform(p)}).catch(e=>{if(!c.signal.aborted)setError(e.message)}).finally(()=>{if(!c.signal.aborted)setLoading(false)});return()=>c.abort()},[owner]);
  const options=[...platform.filter(p=>p.isBuiltin&&p.isActive).map(p=>({kind:'platform' as const,id:p.datasetKey,name:p.displayName})),...versions.filter(v=>Number(v.ownerId)===Number(owner)).map(v=>({kind:'version' as const,id:v.id,name:`${v.name} · ${v.versionName}`}))].filter(s=>!rows.some(r=>key(r)===key(s)));
  async function generate(s:Source){setBusy(true);setError('');try{const query=s.kind==='version'&&schema?.table&&subjectColumn!=null?`?subjectColumn=${subjectColumn}`:'';await request(`/api/research/archives/${key(s)}${query}`,{method:'PUT'});setRows(await request<Source[]>('/api/research/archives'));inspect(s);select(undefined);setImporting(false)}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
  const source=options.find(s=>key(s)===selection);
  const [schema,setSchema]=useState<{key:string;table?:Grid}>(),[subjectColumn,setSubjectColumn]=useState<number>();
  useEffect(()=>{setSchema(undefined);setSubjectColumn(undefined);if(!selection?.startsWith('version/'))return;
    const c=new AbortController();request<{table?:Grid}>(`/api/research/versions/${selection.slice(8)}`,{signal:c.signal}).then(v=>{if(!c.signal.aborted)setSchema({key:selection,table:v.table})}).catch(e=>{if(!c.signal.aborted)setError(e.message)});return()=>c.abort();
  },[selection]);
  const picker=<Select aria-label="档案分类" style={{width:220}} loading={loading} value={open?key(open):''} options={[{value:'',label:'平台档案'},...rows.map(s=>({value:key(s),label:s.name}))]} onChange={value=>inspect(rows.find(s=>key(s)===value))}/>;
  return <>{error&&!importing&&<Alert type="error" message={error}/>} {open?.kind==='version'?<><Space wrap style={{marginBottom:12}}>{picker}<Button icon={<FolderPlus size={16}/>} onClick={()=>setImporting(true)}>从数据集生成档案</Button></Space><PersonalArchives key={open.id} id={open.id}/></>:<ArchivePage key={open?.id??''} dataset={open?.id} classification={picker} onImport={()=>setImporting(true)} canManage={canManage}/>}
  <Modal title="从数据集生成档案" open={importing} onCancel={()=>setImporting(false)} okText="生成档案" cancelText="取消" confirmLoading={busy} okButtonProps={{disabled:!source||(source.kind==='version'&&schema?.key!==selection)}} onOk={()=>source&&generate(source)}>{error&&<Alert type="error" message={error}/>}<Select aria-label="档案来源" showSearch optionFilterProp="label" placeholder="选择系统或自己的数据集" style={{width:'100%'}} value={selection} onChange={v=>{select(v);setError('')}} options={options.map(s=>({value:key(s),label:`${s.kind==='platform'?'系统':'我的'} · ${s.name}`}))}/>
  {schema?.table&&schema.key===selection&&<label>账号列（可选）<Select allowClear aria-label="账号列" placeholder="按数据行分别建档" style={{width:'100%'}} value={subjectColumn} onChange={setSubjectColumn} options={schema.table.columns.map((name,value)=>({value,label:`第 ${value+1} 列 · ${name}`,disabled:schema.table?.labelColumn===value||schema.table?.postColumns?.includes(value)}))}/></label>}</Modal></>;
}