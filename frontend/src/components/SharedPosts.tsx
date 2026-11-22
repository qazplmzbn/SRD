import {useState} from 'react';
import {Empty,Image,Select,Tag} from 'antd';
import {sample} from './MobileFeed';

export type SharedPost={text:string;date?:string;platform?:string};
const source=(p:SharedPost)=>p.platform?.replace(/^(抖音|微博)同步$/,'$1')||'平台未记录';
export default function SharedPosts({posts}:{posts:SharedPost[]}){
  const [filter,setFilter]=useState<string>();
  if(!posts.length)return <Empty description="本次预约没有共享贴文"/>;
  const visible=posts.filter(p=>!filter||source(p)===filter);
  return <section><div className="care-selectors"><Select allowClear aria-label="按平台筛选贴文" placeholder="全部平台" value={filter} onChange={setFilter} style={{minWidth:160}} options={[...new Set(posts.map(source))].map(value=>({value,label:value}))}/><span>{visible.length} 条贴文</span></div>
    {visible.map((p,i)=>{const m=sample(p.text);return <article className="care-post" key={i}><Tag>{source(p)}</Tag><small>{p.date}</small><p>{m?.caption??p.text}</p>{m&&(m.video?<video controls playsInline preload="metadata" src={m.url}/>:<Image width={300} style={{maxWidth:'100%'}} alt={m.caption||'共享图片'} src={m.url}/>)}</article>})}</section>;
}