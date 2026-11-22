export default function VisitBrief({posts,results}:{posts:{text:string;date?:string}[];results:{name:string;score:number;date?:string}[]}) {
  const chars=posts.reduce((sum,p)=>sum+Array.from(p.text.trim()).length,0);
  return <section><dl className="care-stats">{[['共享贴文',posts.length],['贴文字数',chars],['测评次数',results.length],['量表种类',new Set(results.map(r=>r.name)).size]].map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><h3>量表评分</h3>{results.length?<ul>{results.map((r,i)=><li key={i}>{r.name} · <strong>{r.score} 分</strong>　{r.date}</li>)}</ul>:<p className="pro-muted">本次预约没有共享测评，暂无评分。</p>}
  </section>;
}