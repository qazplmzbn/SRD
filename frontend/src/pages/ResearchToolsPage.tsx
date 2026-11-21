import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Input, Select, Table, Tag, message } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import { getAuthHeader } from '../api';

const raw=import.meta.env.VITE_API_BASE;
const base=!raw || raw==='undefined' ? '' : raw.replace(/\/api\/?$/, '').replace(/\/$/,'');
async function call(path:string,body?:unknown) {
  const response=await fetch(base+path,{method:body?'POST':'GET',headers:{...getAuthHeader(),'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});
  const result=await response.json();
  if(!response.ok || !result.success) throw new Error(result.message || '请求失败');
  return result.data;
}
export default function ResearchToolsPage({mode, embedded=false}:{mode:'datasets'|'models'; embedded?:boolean}) {
  const { state } = useLocation();
  const [rows,setRows]=useState<Record<string,any>[]>([]); const [loading,setLoading]=useState(true);
  const [error,setError]=useState(''); const [query,setQuery]=useState(''); const [version,setVersion]=useState(0);
  const [model,setModel]=useState<number>(); const [text,setText]=useState(() => mode==='models' && typeof state?.sampleText==='string' ? state.sampleText.slice(0,8000) : ''); const [consent,setConsent]=useState(false);
  const [running,setRunning]=useState(false); const [result,setResult]=useState<{response:string;elapsedMs:number}|null>(null);
  useEffect(()=>{let active=true;setRows([]);setError('');setLoading(true);setResult(null);setModel(undefined);setConsent(false);
    call(mode==='datasets'?'/api/datasets':'/api/research/models').then(data=>{if(active)setRows(data)}).catch(e=>{if(active)setError(e.message)}).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};},[mode,version]);
  const filtered=rows.filter(row=>String(row.displayName || row.modelName || '').toLowerCase().includes(query.toLowerCase()));
  return <div className="pro-page">{!embedded&&<div className="pro-page-heading"><h1>{mode==='datasets'?'数据目录':'模型试用'}</h1></div>}
    {error && <Alert type="error" message={error} style={{marginBottom:16}} />}
    {mode==='datasets'?<section className="pro-panel"><Input.Search aria-label="搜索数据集" placeholder="搜索数据集名称" value={query} onChange={e=>setQuery(e.target.value)} style={{maxWidth:360,marginBottom:20}} />
      <Table rowKey="datasetKey" loading={loading} dataSource={filtered} scroll={{x:900}} pagination={{pageSize:10}} columns={[
        {title:'数据集',dataIndex:'displayName',render:(value,row)=><div><strong>{value}</strong><p className="pro-muted">{row.datasetKey}</p></div>},
        {title:'语言',dataIndex:'language'}, {title:'标签体系',dataIndex:'classSystem'}, {title:'账号数',dataIndex:'totalUsers'}, {title:'贴文数',dataIndex:'totalPosts'},
        {title:'来源',dataIndex:'isBuiltin',render:value=><Tag>{value?'内置数据':'导入数据'}</Tag>},
        {title:'说明',dataIndex:'description',ellipsis:true}, {title:'操作',render:(_,row)=><Link to={`/research-data?${new URLSearchParams({view:'archives',dataset:row.datasetKey})}`}>查看样本</Link>},
      ]} /></section>
      :<div className="pro-split"><section className="pro-panel"><h2 className="pro-section-title" style={{marginTop:0}}>在线试用</h2>
        <p className="pro-muted">API / Ollama 模型接收文本，适合先检查输出，再进行批量分类评估。模型可选不代表服务一定在线，调用失败会明确提示。</p><label>选择模型</label><Select aria-label="选择试用模型" loading={loading} value={model} onChange={value=>{setModel(value);setConsent(false);setResult(null)}} disabled={running} style={{width:'100%',margin:'10px 0 20px'}} options={rows.map(row=>({value:row.id,label:`${row.modelName} · ${row.modelType}${row.supportsTrial?'':'（暂不支持文字试用）'}`,disabled:!row.supportsTrial}))} />
        {model && <p className="pro-note">{rows.find(row=>row.id===model)?.description || "此模型暂无说明，请向管理员确认适用范围。"}</p>}<label>输入内容</label><Input.TextArea aria-label="试用内容" rows={9} maxLength={8000} showCount value={text} disabled={running} onChange={e=>{setText(e.target.value);setConsent(false)}} />
        <Checkbox style={{margin:'24px 0 16px'}} checked={consent} disabled={running} onChange={e=>setConsent(e.target.checked)}>我有权使用此内容，同意将其发送给所选模型服务进行分析。</Checkbox>
        <Button type="primary" loading={running} disabled={!model||!text.trim()||!consent} onClick={async()=>{setRunning(true);setResult(null);try{setResult(await call(`/api/research/models/${model}/trial`,{text,consent}))}catch(e){message.error(e instanceof Error?e.message:'调用失败')}finally{setRunning(false)}}}>运行试用</Button>
      </section><section className="pro-panel"><h2 className="pro-section-title" style={{marginTop:0}}>模型输出</h2>{result?<><Tag>耗时 {result.elapsedMs} ms</Tag><p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere',marginTop:18}}>{result.response || '模型返回空内容'}</p></>:<p className="pro-muted">运行后在这里显示结果。试用不会自动创建评测指标。</p>}<p className="pro-note" style={{marginTop:24}}>自研 Emocc / FeaLearner 需要匹配研究账号及专用特征，暂不支持单条文本试用。文字试用失败不会以规则结果替代。</p></section></div>}
  </div>;
}
