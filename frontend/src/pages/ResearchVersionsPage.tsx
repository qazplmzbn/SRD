import { useEffect,useState } from 'react';
import { Alert,Button,Checkbox,Drawer,Form,Input,InputNumber,Modal,Pagination,Space,Table,message } from 'antd';
import { getAuthHeader } from '../api';
import { useAuthStore } from '../store/authStore';

import {ColumnRoles,FreeDataset,GridView,sendTable,type Grid,type Draft} from '../components/FreeDataset';
import {Link} from 'react-router-dom';
import { Download } from 'lucide-react';
import ResearchProbe from '../components/ResearchProbe';
import { downloadResearchJson } from '../researchDownload';
const raw=import.meta.env.VITE_API_BASE;
const base=!raw||raw==='undefined'?'':raw.replace(/\/api\/?$/,'').replace(/\/$/,'');
async function call(path:string,body?:unknown){const r=await fetch(`${base}/api/research/versions${path}`,{method:body?'POST':'GET',headers:{...getAuthHeader(),'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});const data=await r.json();if(!r.ok||!data.success)throw new Error(data.message||'请求失败');return data.data;}
type Sample={subjectId:string;text:string;label:number;split:'TRAIN'|'TEST'};
type Version={id:string;ownerId:number;name:string;versionName:string;source:string;usageScope:string;checksum:string;sampleCount:number;samples?:Sample[];table?:Grid};
export default function ResearchVersionsPage({embedded=false}:{embedded?:boolean}){
 const userId=useAuthStore(s=>s.user?.id);const [rows,setRows]=useState<Version[]>([]);const [total,setTotal]=useState(0);const [page,setPage]=useState(1);const [refresh,setRefresh]=useState(0);const [error,setError]=useState('');const [loading,setLoading]=useState(true);
 const [open,setOpen]=useState(false);const [form]=Form.useForm();const [draft,setDraft]=useState<Draft>();const [consent,setConsent]=useState(false);const [saving,setSaving]=useState(false);
 const [selected,setSelected]=useState<Version|null>(null);const [grants,setGrants]=useState<{userId:number;username:string}[]>([]);const [target,setTarget]=useState<number|null>(null);
 useEffect(()=>{let active=true;setLoading(true);setError('');call(`?page=${page}`).then(data=>{if(active){setRows(data.items);setTotal(data.total)}}).catch(e=>{if(active)setError(e.message)}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[page,refresh]);
 const inspect=async(id:string)=>{setSelected(null);setGrants([]);try{const value=await call(`/${id}`);setSelected(value);if(value.ownerId===userId)setGrants(await call(`/${id}/grants`))}catch(e){message.error(e instanceof Error?e.message:'读取失败')}};
 const grant=async(userId:number,revoke:boolean)=>{if(!selected)return;setSaving(true);try{await call(`/${selected.id}/grants`,{userId,revoke});setGrants(await call(`/${selected.id}/grants`));setTarget(null);message.success(revoke?'已撤销访问':'已授予访问')}catch(e){message.error(e instanceof Error?e.message:'操作失败')}finally{setSaving(false)}};
 return <div className="pro-page"><div className="pro-page-heading"><div>{!embedded&&<h1>数据版本</h1>}</div><Button type="primary" onClick={()=>{form.resetFields();setDraft(undefined);setConsent(false);setOpen(true)}}>导入数据集</Button></div>
 {error&&<Alert type="error" message={error} action={<Button onClick={()=>setRefresh(v=>v+1)}>重试</Button>}/>}
 <section className="pro-panel"><Table rowKey="id" dataSource={rows} loading={loading} pagination={false} scroll={{x:850}} columns={[{title:'数据集',dataIndex:'name',render:(name,row)=><Link to={'/research-data?view=detail&version='+encodeURIComponent(row.id)}>{name}</Link>},{title:'版本',dataIndex:'versionName'},{title:'样本数',dataIndex:'sampleCount'},{title:'来源',dataIndex:'source',ellipsis:true},{title:'使用范围',dataIndex:'usageScope',ellipsis:true},{title:'操作',render:(_,row)=><Button onClick={()=>inspect(row.id)}>查看版本</Button>}]} /><Pagination current={page} total={total} pageSize={20} showSizeChanger={false} onChange={setPage} style={{marginTop:20}}/><p className="pro-muted" style={{marginTop:16}}>这里的版本独立于旧研究目录，不自动复制个人用户资料。版本保存后不可修改，修订需创建新版本。</p></section>
 <Modal width="min(760px, 96vw)" destroyOnHidden title="导入数据集" open={open} onCancel={()=>{if(!saving)setOpen(false)}} footer={null}>
 <Form layout="vertical" form={form} onFinish={async values=>{if(!consent||draft?.labelColumn==null||!draft.postColumns?.length)return;setSaving(true);try{const body=new FormData();body.append('file',draft.file);body.append('meta',new Blob([JSON.stringify({...values,consent,columns:draft.columns,labelColumn:draft.labelColumn,postColumns:draft.postColumns})],{type:'application/json'}));await sendTable('table',body);setOpen(false);setPage(1);setRefresh(v=>v+1);message.success('版本已保存')}catch(e){message.error(e instanceof Error?e.message:'保存失败')}finally{setSaving(false)}}} disabled={saving}>
 <Form.Item label="数据集名称" extra="同名数据集按 v1、v2 顺序保存。" name="name" rules={[{required:true,whitespace:true}]}><Input maxLength={100}/></Form.Item>
 <Form.Item label="数据来源" name="source" rules={[{required:true,whitespace:true}]}><Input maxLength={500}/></Form.Item><Form.Item label="允许的使用范围" name="usageScope" rules={[{required:true,whitespace:true}]}><Input.TextArea maxLength={1000} rows={2}/></Form.Item>
 <FreeDataset draft={draft} onChange={next=>{setDraft(next);setConsent(false)}}/>
 {draft&&<ColumnRoles draft={draft} onChange={next=>{setDraft(next);setConsent(false)}}/>}
 <Checkbox checked={consent} disabled={saving} style={{margin:'20px 0'}} onChange={e=>setConsent(e.target.checked)}>我确认有权导入这些数据，且已核对来源和使用范围。</Checkbox><div className="dataset-import-footer"><Button type="primary" htmlType="submit" loading={saving} disabled={!consent||draft?.labelColumn==null||!draft.postColumns?.length}>保存数据集</Button></div>
 </Form></Modal>
 <Drawer title="版本详情与权限" width="min(880px, 96vw)" open={!!selected} onClose={()=>{if(!saving)setSelected(null)}}>{selected&&<><h2>{selected.name} · {selected.versionName}</h2><p className="pro-muted">来源：{selected.source}</p><p className="pro-muted">使用范围：{selected.usageScope}</p><p className="pro-muted" style={{overflowWrap:'anywhere'}}>内容校验值：{selected.checksum}</p><Button icon={<Download size={16}/>} onClick={()=>downloadResearchJson(`${selected.name}-${selected.versionName}`,selected)}>下载版本 JSON</Button>
 {selected.table?<GridView grid={selected.table}/>:<><Table rowKey={(_,i)=>String(i)} dataSource={selected.samples} pagination={{pageSize:10}} columns={[{title:'账号',dataIndex:'subjectId'},{title:'贴文',dataIndex:'text'},{title:'标签',dataIndex:'label'},{title:'划分',dataIndex:'split'}]}/><ResearchProbe key={selected.id} id={selected.id} samples={selected.samples||[]}/></>}
 {selected.ownerId===userId&&<><h3 className="pro-section-title">授权访问此版本</h3><p className="pro-muted">填写对方提供的专业账号ID。授权只限此版本，可随时撤销后续访问。</p><Space><InputNumber<number> aria-label="被授权账号ID" min={1} precision={0} value={target} onChange={setTarget} disabled={saving}/><Button disabled={!target||saving} onClick={()=>target&&grant(target,false)}>授权访问</Button></Space>{grants.map(g=><div key={g.userId} className="pro-history-head" style={{marginTop:16}}><span>{g.username}（ID {g.userId}）</span><Button danger disabled={saving} onClick={()=>grant(g.userId,true)}>撤销访问</Button></div>)}</>}</>}</Drawer></div>;
}
