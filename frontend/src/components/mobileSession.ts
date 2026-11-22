import { API_BASE } from '../api';
const key='vis4srd.mobile-session.v1';
type Session={name:string;token:string;expires?:string};
export function readSession():Session|null {
  try {const s=JSON.parse(localStorage.getItem(key)||'null');return s&&typeof s.name==='string'&&s.token&&typeof s.token==='string'&&(!s.expires||Date.parse(s.expires)>Date.now())?s:null}catch{return null}
}
export function saveSession(s:Session|null){if(s)localStorage.setItem(key,JSON.stringify(s));else localStorage.removeItem(key);window.dispatchEvent(new Event(key))}
export function scopedKey(base:string){const s=readSession();return base+(s?':account:'+encodeURIComponent(s.name):':guest')}
export function watchSession(fn:()=>void){window.addEventListener(key,fn);window.addEventListener('storage',fn);return ()=>{window.removeEventListener(key,fn);window.removeEventListener('storage',fn)}}
export async function mobileCall<T>(path:string,method='GET',data?:unknown,signal?:AbortSignal):Promise<T>{
  const token=readSession()?.token;if(!token){saveSession(null);throw Error('请重新登录')}
  const res=await fetch(API_BASE+path,{method,signal,cache:'no-store',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:data===undefined?undefined:JSON.stringify(data)});
  const body=await res.json().catch(()=>null);
  if(readSession()?.token!==token)throw Error('账号已切换');
  if(res.status===401){saveSession(null);throw Error('登录已过期，请重新登录')}
  if(!res.ok||!body||body.success===false)throw Error(body?.message||'请求失败，请重试');return body.data;
}