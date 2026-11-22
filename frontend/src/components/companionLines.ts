import {mobileCall,readSession} from './mobileSession';
import type {LocalPost} from './MobileRecords';

export type PetAction='摸摸'|'喂食'|'玩球'|'打盹'|'叫醒';
export const lines:Record<PetAction,string[]>={摸摸:['再摸一下，头顶的小芽也想蹭蹭。','呼噜声开小一点，刚好你能听见。'],喂食:['咔嚓！胡须上还挂着一粒鱼干屑。','小鱼干收到了，两只爪爪捧着吃。'],玩球:['扑到了！咦，球怎么从肚皮底下滚走了？','左爪一拨，右爪接住！'],打盹:['我把尾巴卷成枕头啦。','眼睛合上，小芽也歇一会儿。'],叫醒:['醒啦，先把四只爪爪伸直。','耳朵先醒了，尾巴还想赖床。']};

export async function personalLine(local:LocalPost[],signal:AbortSignal){
  const token=readSession()?.token;
  if(!token)return '';
  const eligible=new Set(['个人记录']);
  const sources=['抖音','微博'];
  const status=await Promise.allSettled(['douyin','weibo'].map(id=>mobileCall<{connection:{status:string}[]}>(`/api/me/sync/${id}`,'GET',undefined,signal)));
  status.forEach((r,i)=>{if(r.status==='fulfilled'&&r.value.connection.some(c=>['CURRENT','SYNCING','RETRY'].includes(c.status)))eligible.add(sources[i])});
  const posts=await mobileCall<{items:{source:string;content:string;publishedAt?:string;importedAt:string}[]}>('/api/me/posts?page=1&pageSize=20','GET',undefined,signal).catch(()=>({items:[]}));
  const candidates=[...local.filter(p=>eligible.has(p.platform)).map(p=>({text:p.text,date:p.date})),
    ...posts.items.filter(p=>eligible.has(p.source.replace(/同步$/,''))).map(p=>({text:p.content,date:p.publishedAt||p.importedAt}))];
  const text=candidates.filter(p=>p.text?.trim()).sort((a,b)=>(Date.parse(b.date)||0)-(Date.parse(a.date)||0))[0]?.text;
  if(!text||signal.aborted||readSession()?.token!==token)return '';
  return mobileCall<string>('/api/me/companion/line','POST',{excerpt:text.trim().slice(0,240)},signal);
}