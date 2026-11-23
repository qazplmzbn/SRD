import {useAuthStore} from './store/authStore';

export const reportPrompts = ['根据贴文描述可观察到的变化、依据、不确定性和随访建议。','重点区分自述与引用、风险因素与保护因素，并列出需要人工核实的问题。'];
export type Cloud = {provider:string;model:string;key:string;prompt:string};
let current: {owner:number;cloud:Cloud}|undefined;
useAuthStore.subscribe((next,prev)=>{if(next.user?.id!==prev.user?.id)current=undefined});
export const readCloud=(id:number)=>current?.owner===id?current.cloud:undefined;
export function configureCloud(owner:number,cloud:Cloud){current={owner,cloud:{...cloud}}}