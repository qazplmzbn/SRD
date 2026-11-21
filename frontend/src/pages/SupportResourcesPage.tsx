import { Link } from 'react-router-dom';
import { Layers3, BookOpen, MapPin, ArrowRight } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { canUseResearch } from '../access';
export default function SupportResourcesPage(){
 const professional=canUseResearch(useAuthStore(s=>s.user?.role));
 const cards=[...(!professional?[{title:'智能问答',icon:Layers3,path:'/chat',description:'通过智能问答使用平台配置的模型服务。',action:'进入智能问答'}]:[]),{title:'知识库',icon:BookOpen,path:'/knowledge',description:'浏览心理健康知识资料，检索文档并查看原文。',action:'浏览知识资料'},{title:'心理援助地图',icon:MapPin,path:'/map',description:'按地区查找心理健康服务机构，查看位置与联系方式。',action:'打开地图'}];
 return <div className="pro-page"><div className="pro-page-heading"><div><div className="pro-eyebrow">SUPPORT RESOURCES</div><h1>支持资源</h1><p className="pro-muted">模型、知识资料与心理援助地图，集中在一个工作区。</p></div></div><div className="pro-card-grid">{cards.map(c=><Link className="pro-feature" to={c.path} key={c.title}><span className="pro-feature-icon"><c.icon size={24}/></span><h3>{c.title}</h3><p>{c.description}</p><span className="pro-feature-action">{c.action}<ArrowRight size={14}/></span></Link>)}</div></div>
}
