import {useState} from 'react';
import {Alert,Button,Checkbox,Space,Table} from 'antd';
import {Play,Download} from 'lucide-react';
import {request} from '../api';
import {downloadResearchJson} from '../researchDownload';

type Sample={text:string;split:string;label:number};
type Row={index:number;expected:number;predicted:number};
type Report={protocol:string;checksum:string;encoder:string;correct:number;baselineCorrect:number;baselineLabel:number;rows:Row[]};
export default function ResearchProbe({id,samples}:{id:string;samples:Sample[]}){
  const [consent,setConsent]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[result,setResult]=useState<Report>();
  const train=samples.filter(s=>s.split==='TRAIN'),test=samples.length-train.length;
  const ready=test>0&&new Set(train.map(s=>s.label)).size===2;
  async function run(){setBusy(true);setError('');setResult(undefined);
    try{setResult(await request<Report>(`/api/research/versions/${encodeURIComponent(id)}/bert-probe`,{method:'POST',body:JSON.stringify({consent})}));}
    catch(e){setError(e instanceof Error?e.message:'检测失败');}finally{setBusy(false);}}
  return <section><h3>本地 BERT 检测</h3><p>训练 {train.length} 条 · 测试 {test} 条 · 冻结编码器，按余弦相似度选择训练类别中心</p>
    {!ready&&<Alert type="info" message="训练集须包含两种标签，测试集至少一条。"/>}
    <Space wrap><Checkbox disabled={busy} checked={consent} onChange={e=>setConsent(e.target.checked)}>允许使用本版本贴文进行本机编码</Checkbox><Button icon={<Play size={15}/>} loading={busy} disabled={!ready||!consent} onClick={run}>运行检测</Button></Space>
    {error&&<Alert type="error" message={error}/>}{result&&<><p>{result.encoder} · 测试集预测正确 {result.correct}/{result.rows.length}；多数类对照（固定预测 {result.baselineLabel}）正确 {result.baselineCorrect}/{result.rows.length}。</p><Button icon={<Download size={15}/>} onClick={()=>downloadResearchJson(`bert-${id}`,{versionId:id,...result})}>下载实验结果</Button><Table rowKey="index" size="small" pagination={{pageSize:5}} dataSource={result.rows} columns={[{title:'测试原文',render:(_,r)=>samples[r.index]?.text},{title:'标注',dataIndex:'expected'},{title:'预测',dataIndex:'predicted'},{title:'复核',render:(_,r)=>r.expected===r.predicted?'一致':r.predicted===1?'误报':'漏报'}]}/></>}
  </section>;
}