import {Capacitor,registerPlugin,type PluginListenerHandle} from '@capacitor/core';
import {readSession,scopedKey} from './mobileSession';

type AndroidOAuth={
  getRedirect():Promise<{url:string}>;
  open(options:{url:string}):Promise<void>;
  addListener(event:'callback',handler:(event:{url:string})=>void):Promise<PluginListenerHandle>;
};

export const androidOAuth=registerPlugin<AndroidOAuth>('DouyinOAuth');
export const hasAndroidOAuth=()=>Capacitor.getPlatform()==='android'&&Capacitor.isPluginAvailable('DouyinOAuth');
export const androidCallbackPath='/mobile/douyin/android-callback';
const pendingKey=()=>scopedKey('vis4srd.douyin-android-oauth');

export async function openAndroidOAuth(url:string,redirect:string){
  const authorization=new URL(url);
  const state=authorization.searchParams.get('state');
  if(!readSession()||!state||authorization.searchParams.get('redirect_uri')!==redirect)throw Error('抖音授权链接无效');
  const key=pendingKey();
  localStorage.setItem(key,JSON.stringify({state,redirect,expires:Date.now()+600_000}));
  try{await androidOAuth.open({url});}catch(e){localStorage.removeItem(key);throw e;}
}

export type DouyinCallback={code:string|null;state:string};
export function takeAndroidCallback(raw:string):DouyinCallback|null{
  if(!readSession())return null;
  try{
    const key=pendingKey();
    const pending=JSON.parse(localStorage.getItem(key)||'null');
    if(!pending)return null;
    if(pending.expires<=Date.now()){localStorage.removeItem(key);return null;}
    const url=new URL(raw);
    const state=url.searchParams.get('state');
    if(url.origin+url.pathname!==pending.redirect||!state||state!==pending.state)return null;
    localStorage.removeItem(key);
    return {code:url.searchParams.get('code'),state};
  }catch{return null;}
}