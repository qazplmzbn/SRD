import { useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, Pagination, Spin, Tag } from 'antd';
import { personalPostsApi, type PostPage } from '../api/personalPosts';
export default function PersonalPostsPage(){
 const [data,setData]=useState<PostPage>(),[page,setPage]=useState(1),[error,setError]=useState(''),[busy,setBusy]=useState(true),[retry,setRetry]=useState(0);
 useEffect(()=>{let active=true;setBusy(true);setError('');personalPostsApi.list(page).then(v=>{if(active)setData(v)}).catch(e=>{if(active)setError(e.message)}).finally(()=>{if(active)setBusy(false)});return()=>{active=false}},[page,retry]);
 return <div className="pro-page"><div className="pro-page-heading"><div><h1>贴文记录</h1><p className="pro-muted">查看当前账号已上传的贴文记录。</p></div></div><Card title={`已上传贴文${data?` · ${data.total} 条`:''}`}>{busy?<Spin/>:error?<Alert type="error" message={error} action={<Button onClick={()=>setRetry(v=>v+1)}>重试</Button>}/>:data?.items.length?<>{data.items.map(p=><article key={p.id} className="pro-review-item"><div><Tag>{p.source}</Tag><p className="pro-muted">{p.publishedAt?new Date(p.publishedAt).toLocaleString():'发布时间未知'}</p><p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{p.content}</p></div></article>)}<Pagination current={page} total={data.total} pageSize={10} showSizeChanger={false} onChange={setPage}/></>:<Empty description="暂无已上传的贴文"/>}</Card></div>
}

