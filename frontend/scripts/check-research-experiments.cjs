const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
(async () => {
 const browser = await chromium.launch({channel:'msedge',headless:true});
 try {
  const context = await browser.newContext({viewport:{width:1440,height:1000}});
  await context.addInitScript(() => localStorage.setItem('vis4srd-auth',JSON.stringify({state:{token:'synthetic-ui-token',isAuthenticated:true,user:{id:101,username:'fixture',role:'researcher'}},version:0})));
  const page=await context.newPage(); const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const version={id:'version1',name:'合成研究数据',versionName:'v1',source:'界面测试合成内容',usageScope:'仅用于测试',checksum:'a'.repeat(64),samples:[{split:'TEST'},{split:'TEST'},{split:'TRAIN'}]};
  let task=null, submitted=null, revoked=false;
  await page.route(/^http:\/\/127\.0\.0\.1:5173\/api\//,async route=>{
   const req=route.request(), path=new URL(req.url()).pathname; let data;
   if(path==='/api/auth/me') data={id:101,username:'fixture',role:'researcher'};
   else if(path==='/api/research/versions')data={items:[version],total:1};
   else if(path==='/api/research/versions/version1')data=version;
   else if(path==='/api/research/models')data=[{id:7,modelName:'合成分类模型',supportsTrial:true}];
   else if(path==='/api/research/experiments'&&req.method()==='POST'){
    submitted=req.postDataJSON();task={id:'experiment1',versionName:'合成研究数据 / v1',datasetChecksum:version.checksum,modelName:'合成分类模型',modelSnapshot:'{"temperature":0}',promptVersion:'binary-post-v1',prompt:'合成测试提示',status:'PARTIAL_FAILED',total:2,completed:2,message:'存在失败样本',createdAt:'2026-11-14T12:00:00Z',metrics:{tp:0,tn:0,fp:0,fn:1,valid:1,failed:1,pending:0,coverage:.5,accuracy:0,precision:null,recall:0,f1:0},results:[{sampleIndex:0,subjectId:'synthetic-a',text:'仅用于界面检查的合成文本A',expected:1,predicted:0,response:'{"label":0}',error:null,elapsedMs:50},{sampleIndex:1,subjectId:'synthetic-b',text:'仅用于界面检查的合成文本B',expected:0,predicted:null,response:'invalid output',error:'输出不是有效的分类JSON',elapsedMs:60}]};data=task.id;
   } else if(path==='/api/research/experiments')data={items:task&&!revoked?[task]:[],total:task&&!revoked?1:0};
   else if(path==='/api/research/experiments/experiment1'){
    if(revoked)return route.fulfill({status:404,json:{success:false,message:'实验不存在或无权访问'}});data=task;
   } else throw new Error('Unexpected API: '+path);
   return route.fulfill({json:{success:true,data}});
  });
  await page.goto('http://127.0.0.1:5173/research-experiments');
  await page.getByRole('heading',{name:'批量实验',exact:true}).waitFor();
  await page.getByRole('button',{name:'新建实验',exact:true}).click();
  await page.getByRole('combobox',{name:'数据版本',exact:true}).click();await page.getByText('合成研究数据 / v1',{exact:true}).click();
  await page.getByText('测试集 2 条，训练集不参与评估',{exact:true}).waitFor();
  await page.getByRole('combobox',{name:'模型',exact:true}).click();await page.getByText('合成分类模型',{exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'创建并运行'}).isEnabled(),false);
  await page.getByRole('checkbox',{name:/我确认有权按上述使用范围处理测试集/}).check();await page.getByRole('button',{name:'创建并运行'}).click();
  await page.getByText('漏报 FN',{exact:true}).waitFor();assert.deepEqual(submitted,{versionId:'version1',modelId:7,consent:true});
  assert.equal(await page.getByText('50.0%',{exact:true}).count(),1);
  assert.equal(await page.getByText('无法计算',{exact:true}).count(),1);
  fs.mkdirSync('output/experiment-qa',{recursive:true});await page.screenshot({path:'output/experiment-qa/results.png',fullPage:true,animations:'disabled'});
  await page.getByText('误判样本（FP + FN）',{exact:true}).click();await page.getByText('调用 / 格式失败',{exact:true}).last().click();
  await page.getByText('synthetic-b',{exact:true}).waitFor();assert.equal(await page.getByText('synthetic-a',{exact:true}).count(),0);
  await page.locator('.ant-table-row-expand-icon').click();await page.getByText('invalid output',{exact:true}).waitFor();
  revoked=true;await page.getByText('实验不存在或无权访问',{exact:true}).waitFor({timeout:10000});
  assert.equal(await page.getByText('invalid output',{exact:true}).isVisible(),false);
  assert.deepEqual(errors,[]);console.log('PASS: create, consent, test split, metrics, error filtering, raw output, revoked-access clearing');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
