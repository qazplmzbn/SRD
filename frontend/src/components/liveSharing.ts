import {mobileCall,readSession,scopedKey} from './mobileSession';
import {readRecords} from './MobileRecords';
import type {AssessmentRecord} from './MobileAssessment';

function snapshot(){const results:AssessmentRecord[]=JSON.parse(localStorage.getItem(scopedKey('vis4srd.mobile-assessments.v1'))||'[]');return {posts:readRecords().map(({text,title,date,platform})=>({text:text||title,date,platform})),results:results.map(({name,date,score})=>({name,date,score}))}}
export async function bookingSnapshot(){
  const local=snapshot();
  const page=await mobileCall<{items:{content:string;source:string;publishedAt?:string;importedAt:string}[]}>('/api/me/posts?page=1&pageSize=100');
  const posts=[...page.items.map(p=>({text:p.content,platform:p.source,date:p.publishedAt||p.importedAt})),...local.posts].slice(0,100);
  return {posts:posts.map(p=>({...p,text:p.text?.slice(0,8000)})),results:local.results.slice(0,100)};
}
let queue=Promise.resolve();
export function syncLive(){const session=readSession();if(!session)return Promise.resolve();const next=queue.then(async()=>{if(readSession()?.token!==session.token)return;
  const current=await mobileCall<{id:string;state:string}[]>('/api/care/appointments');
  const active=current.filter(r=>r.state==='ACCEPTED');
  if(!active.length||readSession()?.token!==session.token)return;
  const data=await bookingSnapshot();
  for(const {id} of active){
    if(readSession()?.token!==session.token) return;
    await mobileCall(`/api/care/appointments/${id}/live/sync`,'PUT',data);
  }
});queue=next.catch(()=>{});return next}