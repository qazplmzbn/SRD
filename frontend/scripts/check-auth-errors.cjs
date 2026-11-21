const fs=require('node:fs'), ts=require('typescript'), Module=require('node:module'), assert=require('node:assert/strict');
const source=fs.readFileSync(require('node:path').join(__dirname,'../src/api/index.ts'),'utf8').replaceAll('import.meta.env','({})');
const mod=new Module('api-test',module);mod.paths=module.paths;mod._compile(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,'api-test');
(async()=>{
 let cleared=false, redirected=false;
 global.localStorage={getItem:()=>JSON.stringify({state:{token:'test-token'}}),removeItem:()=>{cleared=true}};
 global.window={location:{pathname:'/chat',search:'',replace:()=>{redirected=true}}};
 global.fetch=async url=>new Response(JSON.stringify(url.endsWith('/api/auth/me')?{success:true,data:{id:1}}:{message:'no'}),{status:url.endsWith('/api/auth/me')?200:401});
 await assert.rejects(mod.exports.fetchChatSessions(),/登录仍有效/);assert.equal(cleared,false);assert.equal(redirected,false);
 global.fetch=async()=>new Response('{}',{status:401});
 await assert.rejects(mod.exports.fetchChatSessions());assert.equal(cleared,true);assert.equal(redirected,true);
 cleared=false;redirected=false;global.fetch=async url=>{if(url.endsWith('/api/auth/me'))throw new Error('network');return new Response('{}',{status:401})};
 await assert.rejects(mod.exports.fetchChatSessions());assert.equal(cleared,false);
 global.fetch=async()=>new Response(JSON.stringify({success:false,message:'业务失败'}));
 await assert.rejects(mod.exports.fetchChatSessions(),/业务失败/);
 console.log('PASS: valid session retained, expired session redirected, network failure retained, business errors surfaced');
})().catch(e=>{console.error(e);process.exit(1)});

