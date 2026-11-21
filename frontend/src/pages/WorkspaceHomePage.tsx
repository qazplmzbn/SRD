import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Table } from 'antd';
import { FileText, Calendar, User } from 'lucide-react';
import HomeOverview from '../components/HomeOverview';
import PaperStatCard from '../components/PaperStatCard';
import { useAuthStore } from '../store/authStore';
import { canUseResearch } from '../access';
import { personalPostsApi, type PostPage } from '../api/personalPosts';
function PersonalOverview(){
 const user=useAuthStore(s=>s.user);const [data,setData]=useState<PostPage>(),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{let active=true;setError('');personalPostsApi.list(1).then(v=>{if(active)setData(v)}).catch(e=>{if(active)setError(e.message)});return()=>{active=false}},[retry]);
 return <>{error&&<Alert type="error" message={error} action={<Button onClick={()=>setRetry(v=>v+1)}>重试</Button>}/>}<div className="grid grid-cols-1 md:grid-cols-3 gap-4"><PaperStatCard label="已上传贴文" value={error?'—':data?.total??'—'} icon={FileText} tone="blue" note="当前账号全部已上传记录"/><PaperStatCard label="最近上传" value={data?.items[0]?new Date(data.items[0].importedAt).toLocaleDateString():'—'} icon={Calendar} tone="cyan" note="最近一条记录的保存日期"/><PaperStatCard label="当前账号" value={user?.nickname||user?.username||'—'} icon={User} tone="slate" note="统计范围仅包含当前账号"/></div><section className="pro-panel" style={{marginTop:24}}><h2 className="pro-section-title" style={{marginTop:0}}>最近上传记录</h2>{data?.items.length?<Table rowKey="id" pagination={false} dataSource={data.items} columns={[{title:'来源',dataIndex:'source'},{title:'内容',dataIndex:'content',ellipsis:true},{title:'保存时间',dataIndex:'importedAt',render:v=>new Date(v).toLocaleString()}]}/>:<Empty description={error?'暂时无法获取记录':data?'暂无已上传记录':'正在读取记录'}/>}</section></>;
}
export default function WorkspaceHomePage(){
 const role=useAuthStore(s=>s.user?.role);const professional=canUseResearch(role);
 return <div className="pro-page">{professional?<HomeOverview/>:<PersonalOverview/>}</div>;
}
