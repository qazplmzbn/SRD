import { useEffect, useState } from 'react';
import { getScaleByCode, loadScalesData, calculateScaleScore, type ScaleDefinition } from '../scales';
import { Cloud, Moon, ClipboardList, Leaf, Search, ChevronRight } from 'lucide-react';
const catalog = [
 {code:'PHQ-9',title:'抑郁筛查',Icon:Cloud}, {code:'GAD-7',title:'焦虑筛查',Icon:Leaf},
 {code:'ISI',title:'失眠自评',Icon:Moon}, {code:'DASS-21',title:'抑郁焦虑压力自评',Icon:ClipboardList},
];
export type AssessmentRecord={date:string;name:string;score:number};
export function MobileAssessment({onComplete,history=[]}:{onComplete:(r:AssessmentRecord)=>void;history?:AssessmentRecord[]}){
 const [ready,setReady]=useState(false),[query,setQuery]=useState(''),[filter,setFilter]=useState('全部');
 const [scale,setScale]=useState<ScaleDefinition>(),[answers,setAnswers]=useState<Record<number,number>>({}),[done,setDone]=useState<number>(),[error,setError]=useState('');
 useEffect(()=>{loadScalesData().then(()=>setReady(true)).catch(()=>setError('量表暂时无法加载'))},[]);
 if(!scale){
  const rows=catalog.flatMap(({code,title,Icon})=>{
   const def=ready&&getScaleByCode(code); if(!def)return [];
   const short=def.questions.length<=10,completed=history.some(r=>r.name===def.name);
   if(!`${code} ${title} ${def.name}`.toLowerCase().includes(query.trim().toLowerCase())||filter==='短量表'&&!short||filter==='长量表'&&short||filter==='已做'&&!completed)return [];
   return <button className="mh-scale" key={code} onClick={()=>{setScale(def);setAnswers({});setDone(undefined);setError('')}}><span className={'mh-symbol mh-'+code}><Icon size={22}/></span><span><strong>{code} {title}</strong><small>{def.questions.length} 题{def.estimated_minutes?` · 约 ${def.estimated_minutes} 分钟`:''}{completed?' · 已做':''}</small></span><b>开始 <ChevronRight size={14}/></b></button>;
  });
  return <section className="mh-library"><label className="mh-search"><Search size={18}/><input aria-label="搜索量表" type="search" placeholder="搜索量表或维度，如焦虑" value={query} onChange={e=>setQuery(e.target.value)}/></label><div className="mh-tabs" role="group" aria-label="量表筛选">{['全部','短量表','长量表','已做'].map(f=><button key={f} aria-pressed={filter===f} onClick={()=>setFilter(f)}>{f}</button>)}</div>{history[0]&&<p className="mh-recent">最近测评 · {history[0].name} · {history[0].score} 分</p>}{rows}{!rows.length&&<p role="status">{error||(!ready?'正在加载量表…':'没有符合条件的量表')}</p>}<p className="mobile-footnote">结果仅供自我了解，不替代专业诊断。</p></section>;
 }
 return <div className="mobile-records"><button onClick={()=>setScale(undefined)}>返回量表列表</button><h2>{scale.name}</h2>{done!==undefined?<div className="mobile-post"><h2>本次得分 {done}</h2><p>结果已保存到本机测评记录。如持续感到困扰，可以联系专业人员进一步了解。</p><button onClick={()=>{setAnswers({});setDone(undefined)}}>重新测评</button></div>:<><p>{scale.instruction}</p>{scale.questions.map((q,i)=><fieldset className="mobile-post" key={q.id}><legend>{i+1}. {q.text}</legend>{q.options.map(o=><label key={o.value}><input type="radio" name={`q-${q.id}`} checked={answers[q.id]===o.value} onChange={()=>setAnswers(a=>({...a,[q.id]:o.value}))}/> {o.label}</label>)}</fieldset>)}<button disabled={scale.questions.some(q=>answers[q.id]===undefined)} onClick={()=>{try{const score=calculateScaleScore(scale.code,answers,scale.questions).total;onComplete({date:new Date().toISOString(),name:scale.name,score});setDone(score);setError('')}catch{setError('保存失败，请检查本机存储后重试')}}}>完成测评</button>{error&&<p role="alert">{error}</p>}</>}</div>
}
