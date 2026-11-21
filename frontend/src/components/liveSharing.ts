import {mobileCall,readSession,scopedKey} from './mobileSession';
import {readRecords} from './MobileRecords';
import type {AssessmentRecord} from './MobileAssessment';

function key(){const s=readSession();if(!s)throw Error('请先登录');return scopedKey('vis4srd.mobile-live.v2')}
function grants(k:string):Record<string,string>{return JSON.parse(localStorage.getItem(k)||'{}')}
function snapshot(){const results:AssessmentRecord[]=JSON.parse(localStorage.getItem(scopedKey('vis4srd.mobile-assessments.v1'))||'[]');return {posts:readRecords().map(({text,title,date})=>({text:text||title,date})),results:results.map(({name,date,score})=>({name,date,score}))}}
export async function enableLive(id:string){const k=key(),source=crypto.randomUUID();await mobileCall(`/api/care/appointments/${id}/live/enable`,'PUT',{source,snapshot:snapshot()});localStorage.setItem(k,JSON.stringify({...grants(k),[id]:source}))}
export async function stopLive(id:string){const k=key();await mobileCall(`/api/care/appointments/${id}/live`,'DELETE');const g=grants(k);delete g[id];localStorage.setItem(k,JSON.stringify(g))}
let queue=Promise.resolve();
export function syncLive(){const session=readSession();if(!session)return Promise.resolve();const next=queue.then(async()=>{if(readSession()?.token!==session.token)return;const k=key(),entries=Object.entries(grants(k));if(!entries.length)return;
  const current=await mobileCall<{id:string;state:string;liveSharing:boolean}[]>('/api/care/appointments');
  for(const [id,source] of entries){if(readSession()?.token!==session.token||grants(k)[id]!==source)continue;
    if(!current.some(r=>r.id===id&&r.state==='ACCEPTED'&&r.liveSharing)){const g=grants(k);delete g[id];localStorage.setItem(k,JSON.stringify(g));continue}
    await mobileCall(`/api/care/appointments/${id}/live/sync`,'PUT',{source,snapshot:snapshot()});
  }
});queue=next.catch(()=>{});return next}