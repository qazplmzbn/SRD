import {useEffect,useState} from 'react';
import {useLocation} from 'react-router-dom';
import {ModelCenterTabs} from './ModelCenterPage';
import {Alert,Button,Checkbox,Radio,Spin,message} from 'antd';
import {Bot,Cloud,Save} from 'lucide-react';
import {readCloud,configureCloud,reportPrompts} from '../researchCloud';
import {Input,Select,Space} from 'antd';
import {request} from '../api';
import {useAuthStore} from '../store/authStore';
import {readPreferences,savePreferences,questions} from '../professionalPreferences';

export default function PersonalModelSettings(){
  const user=useAuthStore(s=>s.user), template=useLocation().pathname.endsWith('/template');
  return user&&<Preferences key={`${user.id}:${template}`} id={user.id} doctor={user.role==='doctor'} template={template}/>;
}
function Preferences({id,doctor,template}:{id:number;doctor:boolean;template:boolean}){
  const [prefs,setPrefs]=useState(()=>readPreferences(id));
  const [cloud,setCloud]=useState(()=>readCloud(id)??{provider:'dashscope',model:'qwen-flash',key:'',prompt:reportPrompts[0]});
  const [models,setModels]=useState<{id:number;modelName:string;modelType:string;supportsTrial:boolean;description?:string}[]>([]);
  const [error,setError]=useState(''),[loading,setLoading]=useState(!template),[attempt,retry]=useState(0);
  useEffect(()=>{
    if(template)return;
    const controller=new AbortController();setLoading(true);setError('');
    request<typeof models>('/api/research/models',{signal:controller.signal}).then(setModels)
      .catch(e=>{if(!controller.signal.aborted)setError(e.message)})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false)});
    return ()=>controller.abort();
  },[template,attempt]);
  const panel='overflow-hidden rounded-2xl border border-[#E2E8F0] bg-white shadow-sm';
  const row='flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 border-b border-[#E2E8F0] last:border-0';
  const groups=[{name:'大模型',type:'api',Icon:Cloud}];
  return <section className="model-center-original flex flex-1 flex-col min-h-0 w-full gap-4 md:gap-5 animate-fade-in">
    <ModelCenterTabs base="/model"/>
    {!doctor&&!template&&<section className={panel}><div className={row}><strong>自有档案报告模型</strong><small>当前登录会话，刷新后需重新配置</small></div><Space wrap className="p-4">
      <Select aria-label="云服务" value={cloud.provider} options={['dashscope','openai','deepseek'].map(value=>({value,label:value}))} onChange={provider=>setCloud({...cloud,provider,key:'',model:''})}/>
      <Input aria-label="模型 ID" placeholder="模型 ID" value={cloud.model} onChange={e=>setCloud({...cloud,model:e.target.value})}/>
      <Input.Password aria-label="API Key" placeholder="API Key" autoComplete="off" value={cloud.key} onChange={e=>setCloud({...cloud,key:e.target.value})}/>
      <Select aria-label="报告提示词" value={cloud.prompt} options={reportPrompts.map(value=>({label:value,value}))} onChange={prompt=>setCloud({...cloud,prompt})}/>
      <Button icon={<Save size={16}/>} disabled={!cloud.key.trim()||!cloud.model.trim()} onClick={()=>{configureCloud(id,cloud);message.success('个人报告配置已生效')}}>应用报告配置</Button></Space></section>}
    <header className={`${panel} ${row}`}><div><h1 className="text-lg font-semibold">{template?'提示词配置':'模型配置'}</h1><small className="pro-muted">个人默认配置 · 当前浏览器</small></div><Button icon={<Save size={16}/>} type="primary" onClick={()=>{try{savePreferences(id,prefs);message.success('默认配置已保存')}catch{message.error('无法保存到当前浏览器')}}}>保存默认配置</Button></header>
    {template?<><section className={panel}><h2 className="bg-blue-50 px-5 py-3 font-semibold">研究实验 · 分类判定模板</h2>
      {([{key:'speaker',label:'区分自述、转述与引用'},{key:'negation',label:'核对否定词的对象和范围'}] as const).map(rule=><div className={row} key={rule.key}><Checkbox checked={prefs[rule.key]} onChange={e=>setPrefs({...prefs,[rule.key]:e.target.checked})}>{rule.label}</Checkbox><small className="pro-muted">补充判定步骤</small></div>)}</section>
      {doctor&&<section className={panel}><h2 className="bg-teal-50 px-5 py-3 font-semibold">诊前提纲 · 默认问题</h2><Radio.Group className="w-full" value={prefs.question} onChange={e=>setPrefs({...prefs,question:e.target.value})}>{questions.map(q=><div key={q} className={row}><Radio value={q}>{q}</Radio></div>)}</Radio.Group></section>}</>:
      <><Button className="self-start" disabled={prefs.model==null} onClick={()=>setPrefs({...prefs,model:undefined})}>清除默认模型</Button>
      {error?<Alert type="error" message={error} action={<Button onClick={()=>retry(v=>v+1)}>重试</Button>}/>:loading?<Spin/>:groups.map(({name,type,Icon})=>{
        const items=models.filter(m=>m.supportsTrial&&m.modelType===type);
        return <section className={panel} key={type}><h2 className={`flex items-center gap-3 px-5 py-3 font-semibold ${type==='api'?'bg-blue-50':'bg-teal-50'}`}><Icon size={16}/>{name}<small className="ml-auto">{items.length}</small></h2>
        {items.length?items.map(m=><div key={m.id} className={row}><div className="flex min-w-0 items-center gap-3"><Bot size={18}/><div className="min-w-0 break-words"><strong>{m.modelName}</strong>{m.description&&<p className="pro-muted text-xs">{m.description}</p>}</div></div><Radio aria-label={`选择 ${m.modelName}`} checked={prefs.model===m.id} onChange={()=>setPrefs({...prefs,model:m.id})}>{prefs.model===m.id?'当前选择':'设为默认'}</Radio></div>):<p className="p-5 text-sm pro-muted">暂无可选模型</p>}</section>;
      })}</>}
  </section>;
}