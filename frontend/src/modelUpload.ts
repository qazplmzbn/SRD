export type ModelUpload={userHash:string;dataSource:string;posts:string[];bertEmbeddings:number[][];emojiSequences:string[][]};
export function parseModelUpload(text:string):ModelUpload{
 const v=JSON.parse(text.replace(/^\uFEFF/,''));
 if(!v||typeof v.userHash!=='string'||!v.userHash.trim()||v.userHash.length>100)throw Error('请提供1至100字的用户编号');
 if(!['reddit','weibo','bigdata','sigir'].includes(v.dataSource))throw Error('数据源必须为reddit、weibo、bigdata或sigir');
 if(!Array.isArray(v.posts)||v.posts.length<1||v.posts.length>50||v.posts.some((p:unknown)=>typeof p!=='string'||p.length>10000))throw Error('请提供1至50条文本，每条最多10000字');
 if(!Array.isArray(v.bertEmbeddings)||v.bertEmbeddings.length!==v.posts.length||v.bertEmbeddings.some((row:unknown)=>!Array.isArray(row)||row.length!==768||row.some(n=>typeof n!=='number'||!Number.isFinite(n))))throw Error('每条文本必须对应768维有限数值特征');
 if(!Array.isArray(v.emojiSequences)||v.emojiSequences.length!==v.posts.length||v.emojiSequences.some((row:unknown)=>!Array.isArray(row)||row.length>100||row.some(s=>typeof s!=='string'||s.length>32)))throw Error('每条文本必须对应一个表情数组，每条最多100个表情');
 return {userHash:v.userHash.trim(),dataSource:v.dataSource,posts:v.posts,bertEmbeddings:v.bertEmbeddings,emojiSequences:v.emojiSequences};
}
