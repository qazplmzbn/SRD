import {useState} from 'react';
import {Alert,Button} from 'antd';
import {ScanText} from 'lucide-react';
import {request} from '../api';
type Output={model:string;encoder:string;classIndex:number;weights:number[]};
export default function PostReview({id,posts}:{id:string;posts:{text:string;date?:string}[]}){
 const [data,setData]=useState<Output>(),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function run(){setBusy(true);setError('');setData(undefined);try{const r=await request<Output>(`/api/care/appointments/${encodeURIComponent(id)}/review`,{method:'POST'});if(r.weights.length!==Math.min(32,posts.length)||r.weights.some(x=>!Number.isFinite(x)||x<0||x>1))throw Error('无效的模型输出');setData(r)}catch{setError('未取得检测结果。请确认用户已授权本次模型分析，并检查本机模型服务。')}finally{setBusy(false)}}
 return <section><Button type="primary" icon={<ScanText size={16}/>} loading={busy} disabled={!posts.length} onClick={run}>运行模型检测</Button><p className="pro-muted">仅处理共享顺序前 32 条，每条最多 128 个 token。无需检测也可查看全部共享原文。</p>{error&&<Alert showIcon type="warning" message={error}/>}{data&&<><h3>模型建议先读</h3><p>已分析 {data.weights.length}/{posts.length} 条，未分析 {posts.length-data.weights.length} 条。阅读顺序按相对注意力排列，不代表风险高低。</p><small>{data.model} · {data.encoder}</small>{data.weights.map((weight,i)=>({weight,i})).sort((a,b)=>b.weight-a.weight).map(({i},rank)=><article className="pro-review-item" key={i}><div><strong>#{rank+1}　原文 {i+1}</strong> <small>{posts[i].date}</small><p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{posts[i].text}</p></div></article>)}</>}</section>
}