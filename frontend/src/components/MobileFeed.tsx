import {useState} from 'react';
import {Image} from 'antd';
import {BookOpen} from 'lucide-react';
import type {LocalPost} from './MobileRecords';
const samples=import.meta.glob('../assets/sample-media/*',{eager:true,query:'?url',import:'default'}) as Record<string,string>;
export function sample(text:string){const match=/\n\[附件: ([\w-]+\.(png|webm))\]$/.exec(text);const url=match&&samples[`../assets/sample-media/${match[1]}`];return url&&match?{url,video:match[2]==='webm',caption:text.slice(0,match.index)}:undefined}

export default function MobileFeed({records,grants}:{records:LocalPost[];grants:string[]}) {
  const [platform,setPlatform]=useState('全部');
  const visible=records.filter(p=>(p.platform==='个人记录'||grants.includes(p.platform))&&(platform==='全部'||p.platform===platform));
  return <>
    <div className="feed-filters daily-platforms" role="group" aria-label="切换平台">{['全部',...grants].map(p=><button key={p} aria-pressed={platform===p} onClick={()=>setPlatform(p)}>{p}</button>)}</div>
    {visible.map(post=>{const media=sample(post.text);return <article className={`feed-card feed-${post.kind}`} key={post.id}>
      <div className="feed-author"><span className="feed-avatar" aria-hidden="true">我</span><div><strong>我</strong><time>{post.date}</time></div><span className="feed-platform">{post.platform}</span></div>
      <div className="feed-body">{post.title&&<h3>{post.title}</h3>}<p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere',display:'block'}}>{media?.caption??post.text}</p></div>
      {media&&(media.video?<video src={media.url} controls playsInline preload="metadata" aria-label={media.caption} style={{width:'100%',display:'block'}}/>:<Image width="100%" src={media.url} alt={media.caption}/>)}
      <div className="feed-tags">{post.tags.map(tag=><span key={tag}>#{tag}</span>)}</div>
      <div className="feed-footer"><span>{media?(media.video?'视频记录':'图片记录'):{video:'视频记录',article:'回答与长文',note:'笔记',status:'文字记录'}[post.kind]}</span>{post.sourceUrl&&<a href={post.sourceUrl} target="_blank" rel="noopener noreferrer">打开原链接</a>}</div>
    </article>})}
    {!visible.length&&<section className="daily-empty"><BookOpen size={28}/><h2>{platform==='全部'?'还没有日常记录':`${platform}还没有记录`}</h2></section>}
  </>;
}