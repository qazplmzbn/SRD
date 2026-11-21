import {useState} from 'react';
import {Select} from 'antd';

import {questions,readPreferences} from '../professionalPreferences';
import {useAuthStore} from '../store/authStore';
export default function VisitBrief({posts,results}:{posts:{text:string;date?:string}[];results:{name:string;score:number;date?:string}[]}) {
  const [index,select]=useState(0),[question,setQuestion]=useState(()=>readPreferences(useAuthStore.getState().user?.id).question);
  const post=posts[index];
  return <section><h3>诊前资料</h3><p>本次共享：{posts.length} 条贴文、{results.length} 项测评。</p>
    {posts.length>0&&<><Select aria-label="提纲引用原文" value={index} onChange={select} options={posts.map((p,i)=>({value:i,label:`原文 ${i+1} · ${p.date||'日期未记录'}`}))}/><blockquote style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{post?.text}</blockquote>
    <label>待询问问题 <Select aria-label="待询问问题" style={{maxWidth:'100%',width:420}} value={question} onChange={setQuestion} options={questions.map(value=>({value,label:value}))}/></label></>}
    {results.length>0&&<details><summary>共享测评（{results.length}）</summary>{results.map((r,i)=><p key={i}>{r.date} · {r.name} · {r.score} 分</p>)}</details>}
  </section>;
}