import {useRef,useState} from 'react';
import {Alert,Button,Input,Select,Table} from 'antd';
import {Upload} from 'lucide-react';
import {API_BASE,getAuthHeader} from '../api';

export type Grid={columns:string[];rows:string[][];originalColumns?:string[];labelColumn?:number;postColumns?:number[]};
export type Draft=Grid&{file:File;total:number};
export function ColumnRoles({draft,onChange}:{draft:Draft;onChange:(d:Draft)=>void}){
 const options=draft.columns.map((name,value)=>({value,label:`第 ${value+1} 列${name?` · ${name}`:''}`}));
 return <><h3>指定列用途</h3>{options.length<2&&<Alert type="warning" message="文件至少需要两列，才能分别指定标签和贴文。"/>}<div className="dataset-fields">
 <label>标签列（必选）<Select aria-label="标签列" style={{width:'100%'}} placeholder="选择一个标签列" value={draft.labelColumn} options={options.map(o=>({...o,disabled:draft.postColumns?.includes(o.value)}))} onChange={labelColumn=>onChange({...draft,labelColumn})}/></label>
 <label>贴文列（可多选）<Select aria-label="贴文列" style={{width:'100%'}} mode="multiple" placeholder="选择一个或多个贴文列" value={draft.postColumns??[]} options={options.map(o=>({...o,disabled:o.value===draft.labelColumn}))} onChange={postColumns=>onChange({...draft,postColumns})}/></label>
 </div></>;
}
export async function sendTable(path:string,body:FormData){const r=await fetch(`${API_BASE}/api/research/versions/${path}`,{method:'POST',headers:getAuthHeader(),body});const j=await r.json();if(!r.ok||!j.success)throw Error(j.message||'读取失败');return j.data;}
export function GridView({grid,rename}:{grid:Grid;rename?:(i:number,name:string)=>void}){return <Table<string[]> size="small" rowKey={(_,i)=>String(i)} scroll={{x:Math.max(520,grid.columns.length*150)}} dataSource={grid.rows} pagination={rename?false:{pageSize:10}} columns={grid.columns.map((name,i)=>({key:String(i),width:150,title:rename?<Input aria-label={`第 ${i+1} 列名称`} value={name} placeholder={`第 ${i+1} 列`} onChange={e=>rename(i,e.target.value)}/>:name||`第 ${i+1} 列`,render:(_,row)=>row[i]??''}))}/>}
export function FreeDataset({draft,onChange}:{draft?:Draft;onChange:(d?:Draft)=>void}){const input=useRef<HTMLInputElement>(null),sequence=useRef(0);const [error,setError]=useState(''),[busy,setBusy]=useState(false);
 return <section className="dataset-mapping"><Button loading={busy} icon={<Upload size={16}/>} onClick={()=>input.current?.click()}>{draft?.file.name||'选择 CSV'}</Button><input ref={input} type="file" accept=".csv,text/csv" style={{display:'none'}} onChange={async e=>{const f=e.target.files?.[0];e.target.value='';if(!f)return;const n=++sequence.current;setBusy(true);setError('');onChange();try{const body=new FormData();body.append('file',f);const data=await sendTable('table-preview',body);if(n===sequence.current)onChange({...data,file:f})}catch(err){if(n===sequence.current)setError((err as Error).message)}finally{if(n===sequence.current)setBusy(false)}}}/>{error&&<Alert type="error" message={error}/>}{draft&&<><h3>字段名称 · {draft.columns.length} 列 / {draft.total} 行</h3><GridView grid={draft} rename={(i,name)=>onChange({...draft,columns:draft.columns.map((v,j)=>j===i?name:v)})}/></>}</section>}