import {useEffect,useRef,useState} from 'react';
import {Alert,Button,Input,Table} from 'antd';
import {FileSearch,Upload} from 'lucide-react';
import {API_BASE,getAuthHeader} from '../api';

export type Sample={text:string;label:number;subjectId:string;split:'TRAIN'|'TEST'};
const fields=[['text','贴文','必填','例如：正文'],['label','标签','必填','例如：分类（列值为 0 或 1）'],['subjectId','作者编号','选填','留空则逐行编号'],['split','样本划分','选填','留空则全部作为 TEST']] as const;
export default function DatasetMapping({change}:{change:(rows:Sample[])=>void}){
 const input=useRef<HTMLInputElement>(null), pending=useRef<AbortController>();
 const [file,setFile]=useState<File>(),[mapping,setMapping]=useState({text:'',label:'',subjectId:'',split:''});
 const [rows,setRows]=useState<Sample[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>()=>pending.current?.abort(),[]);
 function reset(){pending.current?.abort();setBusy(false);setRows([]);change([]);setError('')}
 async function preview(){if(!file)return;reset();const controller=new AbortController();pending.current=controller;setBusy(true);
  try{const body=new FormData();body.append('file',file);Object.entries(mapping).forEach(([k,v])=>body.append(k,v.trim()));
   const r=await fetch(`${API_BASE}/api/research/versions/csv-preview`,{method:'POST',headers:getAuthHeader(),body,signal:controller.signal});const result=await r.json();if(!r.ok||!result.success)throw Error(result.message||'读取 CSV 失败');if(!controller.signal.aborted){setRows(result.data);change(result.data)}
  }catch(e){if(!controller.signal.aborted)setError((e as Error).message)}finally{if(!controller.signal.aborted)setBusy(false)}}
 return <section className="dataset-mapping"><h3>CSV 样本文件</h3><Button icon={<Upload size={16}/>} onClick={()=>input.current?.click()}>选择 CSV</Button> <small>{file?.name||'UTF-8 / GBK / 带 BOM 的 UTF-16 · 最多 100 条 · 1 MB 内'}</small>
 <input style={{display:'none'}} ref={input} type="file" accept=".csv,text/csv" onChange={e=>{const f=e.target.files?.[0];e.target.value='';if(!f)return;reset();if(!/\.csv$/i.test(f.name)||f.size>1048576){setFile(undefined);setError('请选择 1 MB 以内的 CSV 文件')}else setFile(f)}}/>
 <h3>平台字段与文件列名</h3><div className="dataset-fields">{fields.map(([key,title,required,hint])=><label key={key}>{title}（{key}，{required}）<Input value={mapping[key]} aria-required={required==='必填'} placeholder={hint} onChange={e=>{reset();setMapping({...mapping,[key]:e.target.value})}}/></label>)}</div>
 <Button style={{marginTop:12}} icon={<FileSearch size={16}/>} loading={busy} disabled={!file||!mapping.text.trim()||!mapping.label.trim()} onClick={preview}>读取并预览</Button>
 {error&&<Alert showIcon type="warning" message={error}/>}{rows.length>0&&<><h3>样本预览 · 共 {rows.length} 条</h3><Table rowKey={(_,i)=>String(i)} size="small" dataSource={rows.slice(0,3)} pagination={false} columns={[{title:'贴文',dataIndex:'text',ellipsis:true},{title:'标签',dataIndex:'label'},{title:'作者',dataIndex:'subjectId'},{title:'划分',dataIndex:'split'}]}/></>}</section>;
}