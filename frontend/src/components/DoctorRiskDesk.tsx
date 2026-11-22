import {useEffect,useRef,useState} from 'react';
import {Alert,Button,Input,Modal,Select,Space,Table} from 'antd';
import {Bot,Brain,Check,Eye,Play,Plus} from 'lucide-react';
import {request,fetchLlmModelsForRiskPage,fetchDetectionModelsForRiskPage,fetchPromptTemplatesForRiskPage} from '../api';
import {ResultPage,type RiskResultSummary} from '../pages/RiskPage';

type Booking={id:string;patientId:number;name:string;scheduledAt:string;state:string;shared:boolean;snapshotVersion:string};
type Report=RiskResultSummary & {posts:{text:string;date?:string}[];model?:string;modelName:string;encoder?:string;promptName?:string;total:number;snapshotVersion:string;processingTimeMs:number};
const categories=[{id:'local_llm',name:'本地 LLM',Icon:Bot},{id:'emocc',name:'检测模型',Icon:Brain}] as const;
type Category=typeof categories[number]['id'];
const detectorDisplayName=(name:string)=>name.replace(/-weibo$/i,'');
type Task={id:string;booking:string;version:string;name:string;category:Category;modelId:string;modelName:string;templateId?:string;createdAt:string;status:'待执行'|'分析中'|'已完成'|'失败';result?:Report;error?:string};
async function loadOptions(){
 const [local,detection,templates]=await Promise.all([fetchLlmModelsForRiskPage(),fetchDetectionModelsForRiskPage(),fetchPromptTemplatesForRiskPage()]);
 const key=(value?:string)=>value?.toLowerCase().replace(/[^a-z0-9]/g,'');
 const weiboModels=detection.filter(m=>{
  if(m.status!=='active'||(m.type!=='emoji'&&m.type!=='fealearner'))return false;
  const expected=m.type==='emoji'?'emoccweibo':'fealearnerweibo';
  return m.supportedDatasets?.length ? m.supportedDatasets.some(ds=>ds.trim().toLowerCase()==='weibo') : key(m.modelCode)===expected||key(m.name)===expected;
 });
 return {local_llm:local.filter(m=>m.status==='active'),emocc:weiboModels.map(m=>({...m,name:detectorDisplayName(m.name)})),templates};
}
const eligible=(b:Booking)=>b.state==='ACCEPTED'&&!!b.shared&&!!b.snapshotVersion;

export default function DoctorRiskDesk(){
 const [bookings,setBookings]=useState<Booking[]>([]),[tasks,setTasks]=useState<Task[]>([]),[error,setError]=useState(''),[loading,setLoading]=useState(true);
 const [open,setOpen]=useState(false),[booking,choose]=useState<string>(),[name,setName]=useState(''),[detail,inspect]=useState<string>(),[patient,filterUser]=useState<number>(),[status,filterStatus]=useState<string>();
 const [options,setOptions]=useState<Awaited<ReturnType<typeof loadOptions>>>(),[configError,setConfigError]=useState('');
 const [category,setCategory]=useState<Category>('local_llm'),[modelId,setModelId]=useState<string>(),[templateId,setTemplateId]=useState<string>();
 useEffect(()=>{if(!open)return;let current=true;setOptions(undefined);setConfigError('');
  loadOptions().then(value=>{if(current)setOptions(value)}).catch(()=>{if(current)setConfigError('无法加载模型配置，请关闭弹窗后重试。')});
  return()=>{current=false};},[open]);
 const calls=useRef(new Map<string,AbortController>());
 useEffect(()=>{const ctrl=new AbortController();let timer:ReturnType<typeof setTimeout>;
  async function load(){try{const rows=await request<Booking[]>('/api/care/appointments',{signal:ctrl.signal,cache:'no-store'});if(ctrl.signal.aborted)return;
    setBookings(rows);setError('');setTasks(ts=>ts.filter(t=>rows.some(b=>eligible(b)&&b.id===t.booking&&b.snapshotVersion===t.version)));
   }catch{if(!ctrl.signal.aborted){setError('无法核验预约状态，已清除本页分析结果。');setTasks([]);setBookings([])}}finally{if(!ctrl.signal.aborted){setLoading(false);timer=setTimeout(load,5000)}}}
  void load();return()=>{ctrl.abort();clearTimeout(timer);calls.current.forEach(c=>c.abort());calls.current.clear()};},[]);
 const available=bookings.filter(eligible),lookup=new Map(available.map(b=>[b.id,b])),users=new Map(available.map(b=>[b.patientId,b.name]));
 const selected=tasks.find(t=>t.id===detail),report=selected?.result;
 const model=options?.[category].find(m=>m.id===modelId);
 const templates=options?.templates??[];
 const canCreate=!!(booking&&lookup.has(booking)&&model&&(category==='emocc'||templates.some(t=>t.id===templateId)));
 const update=(id:string,patch:Partial<Task>)=>setTasks(ts=>ts.map(t=>t.id===id?{...t,...patch}:t));
 async function execute(t:Task){if(calls.current.has(t.id)||!lookup.has(t.booking))return;const ctrl=new AbortController();calls.current.set(t.id,ctrl);update(t.id,{status:'分析中',error:undefined,result:undefined});
  try{const r=await request<Report>(`/api/care/appointments/${encodeURIComponent(t.booking)}/review`,{method:'POST',signal:ctrl.signal,body:JSON.stringify({category:t.category,modelId:Number(t.modelId),templateId:t.templateId?Number(t.templateId):undefined,snapshotVersion:t.version})});
   if(!Array.isArray(r.posts)||r.snapshotVersion.toLowerCase()!==t.version.toLowerCase()||!r.posts.length||!r.summary)throw Error('资料已变化或分析输出无效，请重新创建任务。');
   if(!['low','medium','high'].includes(r.riskLevel)||!Number.isFinite(r.riskScore)||r.riskScore<0||r.riskScore>1)throw Error('未返回有效的风险分类，无法生成报告。');
   update(t.id,{status:'已完成',result:r});}catch(e){if(!ctrl.signal.aborted)update(t.id,{status:'失败',error:e instanceof Error?e.message:'检测失败'})}finally{calls.current.delete(t.id)}}
 if(report && selected){
  const b=lookup.get(selected.booking);
  const modelName=report.inferenceBackend==='heuristic_fallback'?'文本规则回退':selected.category==='emocc'?detectorDisplayName(report.modelName):report.modelName;
  const rawModel=report.model&&selected.category==='emocc'?detectorDisplayName(report.model):report.model;
  if(b) return <ResultPage onBack={()=>inspect(undefined)} appointment={{promptName:report.promptName,total:report.total,posts:report.posts,rawModel,encoder:report.encoder}} task={{id:selected.id,taskName:selected.name,taskMode:selected.category,userHash:`${b.name}（${b.patientId}）`,dataSource:'预约共享贴文',postCount:report.posts.length,modelName,status:'completed',progress:100,resultSummary:report,createdAt:selected.createdAt,completedAt:new Date().toISOString(),processingTimeMs:report.processingTimeMs}}/>;
 }
 return <section className="space-y-3"><dl className="care-stats" aria-label="模型分析统计"><div><dt>可分析用户</dt><dd>{users.size}</dd></div><div><dt>本页任务</dt><dd>{tasks.length}</dd></div><div><dt>执行中</dt><dd>{tasks.filter(t=>t.status==='分析中').length}</dd></div><div><dt>已完成</dt><dd>{tasks.filter(t=>t.status==='已完成').length}</dd></div></dl>
 {error&&<Alert type="error" showIcon message={error}/>}
 <div className="flex flex-wrap items-center gap-3 rounded-lg border bg-white p-3"><Select aria-label="筛选用户" allowClear placeholder="全部用户" style={{minWidth:160}} options={[...users].map(([value,n])=>({value,label:`${n}（${value}）`}))} value={patient} onChange={filterUser}/><Select aria-label="筛选任务状态" allowClear placeholder="全部状态" style={{width:130}} options={['待执行','分析中','已完成','失败'].map(value=>({value,label:value}))} value={status} onChange={filterStatus}/><Button className="ml-auto" type="primary" icon={<Plus size={16}/>} disabled={!available.length} onClick={()=>{choose(undefined);setName('');setOpen(true)}}>新建任务</Button></div>
 <Table<Task> rowKey="id" loading={loading} title={()=>'模型分析任务列表'} scroll={{x:680}} pagination={{pageSize:10,hideOnSinglePage:true}} dataSource={tasks.filter(t=>(!patient||lookup.get(t.booking)?.patientId===patient)&&(!status||t.status===status))} locale={{emptyText:available.length?'暂无本页任务':'暂无已接受且已同步资料的预约'}} columns={[
 {title:'任务名称',dataIndex:'name'},{title:'用户 / 预约时间',render:(_,t)=>{const b=lookup.get(t.booking);return b&&<>{b.name}（{b.patientId}）<div className="pro-muted">{new Date(b.scheduledAt).toLocaleString()}</div></>}},{title:'模型',dataIndex:'modelName'},{title:'状态',render:(_,t)=><>{t.status}{t.error&&<div className="pro-muted">{t.error}</div>}</>},{title:'操作',render:(_,t)=><Space><Button size="small" icon={<Play size={13}/>} disabled={t.status==='分析中'||t.status==='已完成'} onClick={()=>execute(t)}>执行</Button><Button size="small" icon={<Eye size={13}/>} disabled={!t.result} onClick={()=>inspect(t.id)}>查看</Button></Space>}]}/>
 <Modal centered width={672} title={<div className="border-b pb-4"><h2 className="text-lg font-bold">创建检测任务</h2><p className="text-sm font-normal text-slate-500">配置单模型检测参数</p></div>} open={open} onCancel={()=>setOpen(false)} cancelText="取消" okText="创建任务" okButtonProps={{icon:<Check size={16}/>,disabled:!canCreate}} onOk={()=>{const b=booking&&lookup.get(booking);if(!b||!model||!canCreate)return;setTasks(ts=>[{id:crypto.randomUUID(),booking:b.id,version:b.snapshotVersion,name:name.trim()||`${b.name} · 贴文分析`,category,modelId:model.id,modelName:model.name,templateId,createdAt:new Date().toISOString(),status:'待执行'},...ts]);setOpen(false)}}>
  <div className="space-y-5 overflow-y-auto py-3" style={{maxHeight:'calc(100dvh - 200px)'}}>
   <div><p className="mb-2 font-semibold">数据源</p><div className="rounded-lg border bg-slate-50 p-3">本次预约的共享贴文</div></div>
   <div><label htmlFor="analysis-booking" className="mb-2 block font-semibold">目标用户与预约 <span className="text-red-600">*</span></label><Select id="analysis-booking" size="large" showSearch optionFilterProp="label" style={{width:'100%'}} value={booking} placeholder="按姓名、用户编号或预约时间搜索" options={available.map(b=>({value:b.id,label:`${b.name}（${b.patientId}） · ${new Date(b.scheduledAt).toLocaleString()} · ${b.id.slice(0,8)}`}))} onChange={choose}/></div>
   {configError&&<Alert type="error" showIcon message={configError}/>}
   <div><p className="mb-2 font-semibold">模型类型</p><div className="grid grid-cols-2 gap-2 text-center text-sm">{categories.map(({id,name,Icon})=><button key={id} type="button" aria-pressed={category===id} onClick={()=>{setCategory(id);setModelId(undefined);setTemplateId(undefined)}} className={`rounded-lg border py-3 ${category===id?'border-blue-500 bg-blue-50 text-blue-700':'text-slate-600'}`}><Icon className="mx-auto mb-2"/>{name}<br/><small>{options?`${options[id].length} 个配置`:'—'}</small></button>)}</div></div>
   <div><label htmlFor="analysis-model" className="mb-2 block font-semibold">检测模型</label><Select id="analysis-model" className="w-full" loading={!options&&!configError} placeholder="选择已启用的模型" notFoundContent="没有适用于本次预约的已启用模型" value={modelId} options={options?.[category].map(m=>({value:m.id,label:m.name}))} onChange={setModelId}/></div>
   {category==='emocc'?<p className="text-sm text-slate-600">使用所选检测模型的微博权重，无需提示词。</p>:<div><label htmlFor="analysis-template" className="mb-2 block font-semibold">提示词</label><Select id="analysis-template" className="w-full" placeholder="请选择风险检测提示词" notFoundContent="暂无已启用的风险检测提示词" value={templateId} options={templates.map(t=>({value:t.id,label:t.name}))} onChange={setTemplateId}/></div>}
   <label className="block font-semibold">任务名称<Input size="large" className="mt-2" value={name} maxLength={80} placeholder="选填，默认使用患者姓名和分析类型" onChange={e=>setName(e.target.value)}/></label>
  </div></Modal></section>;
}
