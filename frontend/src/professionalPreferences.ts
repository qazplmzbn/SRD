export const questions=['这条记录里，您今天最想谈的是哪一部分？','从写下这条记录到今天，有什么变化？','这段经历对睡眠或日常活动有什么影响？'];
type Preferences={speaker:boolean;negation:boolean;model?:number;question:string};
const key=(id:number)=>`vis4srd.professional-preferences.v1:${id}`;
export function readPreferences(id?:number):Preferences{
  let p;try{p=id==null?{}:JSON.parse(localStorage.getItem(key(id))||'{}')}catch{p={}}
  return {speaker:p?.speaker===true,negation:p?.negation===true,model:Number.isSafeInteger(p?.model)?p.model:undefined,question:questions.includes(p?.question)?p.question:questions[0]};
}
export function savePreferences(id:number,prefs:Preferences){localStorage.setItem(key(id),JSON.stringify(prefs))}