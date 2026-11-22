import {useEffect,useState} from 'react';
import MobileFeed from './MobileFeed';
import type {LocalPost} from './MobileRecords';
import {mobileCall,readSession} from './mobileSession';

type Item={id:string;content:string;source:string;publishedAt?:string;importedAt:string};
export default function DailyFeed({local}:{local:LocalPost[]}) {
  const [grants,setGrants]=useState<string[]>([]);
  const [items,setItems]=useState<Item[]>([]),[error,setError]=useState(''),[limit,setLimit]=useState(20),[total,setTotal]=useState(0);
  useEffect(()=>{
    let active=true,timer:ReturnType<typeof setTimeout>;
    async function load(){
      if(!readSession()){setGrants([]);setItems([]);setTotal(0);return}
      try {
        const statuses=await Promise.allSettled((['douyin','weibo'] as const).map(id=>mobileCall<{connection:{status:string}[]}>(`/api/me/sync/${id}`)));
        if(!active)return;
        setGrants(statuses.flatMap((s,i)=>s.status==='fulfilled'&&s.value.connection.some(c=>['CURRENT','SYNCING','RETRY'].includes(c.status))?[['抖音','微博'][i]]:[]));
        const next:Item[]=[];let count=0;
        for(let page=1;active;page++){
          const res=await mobileCall<{items:Item[];total:number}>(`/api/me/posts?page=${page}&pageSize=20`);
          next.push(...res.items);count=res.total;
          if(next.length>=Math.min(limit,count)||!res.items.length)break;
        }
        if(active){setItems(next);setTotal(count);setError(statuses.some(s=>s.status==='rejected')?'部分平台授权状态读取失败，稍后重试。':'')}
      }catch(e){if(active)setError((e as Error).message)}
      if(active)timer=setTimeout(load,15000);
    }
    void load();return()=>{active=false;clearTimeout(timer)};
  },[limit]);
  const records:LocalPost[]=items.map(p=>({id:`account:${p.id}`,platform:p.source==='抖音同步'?'抖音':p.source==='微博同步'?'微博':p.source,kind:'status',date:p.publishedAt?new Date(p.publishedAt).toLocaleString():'发布时间未知',text:p.content||'无作品文案',media:[],tags:['账号记录']}));
  return <>{error&&<p role="alert">{error}</p>}<MobileFeed key={grants.join(',')} records={[...records,...local]} grants={grants}/>{grants.length>0&&items.length<total&&<button onClick={()=>setLimit(n=>n+20)}>查看更多动态</button>}</>;
}