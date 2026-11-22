import {useEffect,useState} from 'react';
import {Alert,Button,Table} from 'antd';
import {ArrowLeft} from 'lucide-react';
import {request} from '../api';

type Row={userHash?:string;postCount?:number;id?:string;content?:string;riskValue?:number};
export default function PlatformDatasetViewer({dataset}:{dataset:string}){
 const [author,setAuthor]=useState(''),[page,setPage]=useState(1),[rows,setRows]=useState<Row[]>([]),[total,setTotal]=useState(0),[busy,setBusy]=useState(true),[error,setError]=useState('');
 useEffect(()=>{const c=new AbortController();const query=new URLSearchParams({page:String(page),page_size:'10'});if(author)query.set('user_hash',author);
   setBusy(true);setRows([]);setError('');request<{archives?:Row[];posts?:Row[];total:number}>(`/api/datasets/${encodeURIComponent(dataset)}/${author?'posts':'archives'}?${query}`,{signal:c.signal}).then(data=>{if(!c.signal.aborted){setRows((author?data.posts:data.archives)||[]);setTotal(data.total)}}).catch(e=>{if(!c.signal.aborted){setError(e.message);setTotal(0)}}).finally(()=>{if(!c.signal.aborted)setBusy(false)});return()=>c.abort();},[dataset,author,page]);
 return <section>{author&&<Button icon={<ArrowLeft size={15}/>} onClick={()=>{setAuthor('');setPage(1)}}>返回样本账号</Button>}<h3>{author?`${author} · 贴文`:'样本账号'}</h3>{error&&<Alert type="error" message={error}/>}
 <Table<Row> size="small" loading={busy} dataSource={rows} rowKey={r=>String(author?r.id:r.userHash)} pagination={{current:page,total,pageSize:10,showSizeChanger:false,onChange:setPage}} columns={author?[{title:'贴文',dataIndex:'content',render:t=><p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{t}</p>},{title:'标签',dataIndex:'riskValue',render:v=>v??'—',width:72}]:[{title:'账号',dataIndex:'userHash'},{title:'贴文数',dataIndex:'postCount'},{title:'操作',render:(_,r)=><Button type="link" onClick={()=>{setPage(1);setAuthor(r.userHash!)}}>查看贴文</Button>}]}/></section>;
}