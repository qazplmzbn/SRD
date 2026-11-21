const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
 const page=await browser.newPage();
 await page.addInitScript(()=>localStorage.setItem('vis4srd-auth',JSON.stringify({state:{token:'fixture',isAuthenticated:true,user:{id:1,username:'fixture',role:'researcher'}},version:0})));
 let mismatch=false,revoked=false;
 const rows=['a','b'].map(id=>({id,versionName:'synthetic v1',datasetChecksum:'hash',modelName:'Model '+id,promptVersion:'v1',prompt:'fixed',status:'SUCCEEDED',total:2,completed:2,createdAt:'2026-11-15T00:00:00Z',metrics:{coverage:1,accuracy:.5,precision:.5,recall:.5,f1:.5,fp:1,fn:0,failed:0,pending:0}}));
 await page.route(/^http:\/\/127\.0\.0\.1:5173\/api\//,route=>{const path=new URL(route.request().url()).pathname;let data;
 if(path==='/api/auth/me')data={id:1,username:'fixture',role:'researcher'};
 else if(path==='/api/research/experiments')data={items:rows,total:2};
 else {if(revoked)return route.fulfill({status:404,json:{success:false,message:'无权访问'}});data={...rows[path.endsWith('/a')?0:1]};if(mismatch&&data.id==='b')data.datasetChecksum='different';}
 return route.fulfill({json:{success:true,data}});
 });
 await page.goto('http://127.0.0.1:5173/research-experiments');await page.getByText('Model a',{exact:true}).waitFor();
 await page.locator('thead input[type=checkbox]').check();await page.getByRole('button',{name:'对比所选实验（2/4）'}).click();
 await page.getByText('测试集内容及评估协议一致，可结合误报与漏报比较结果。',{exact:true}).waitFor();
 assert.equal(await page.getByRole('dialog').getByText('50.0%',{exact:true}).count(),8);
 mismatch=true;await page.getByText('请选择测试集内容、评估协议和提示词一致的实验。',{exact:true}).waitFor({timeout:10000});
 assert.equal(await page.getByRole('dialog').getByText('50.0%',{exact:true}).count(),0);
 revoked=true;await page.getByText('无权访问',{exact:true}).waitFor({timeout:10000});
 console.log('PASS: comparison metrics, mismatched datasets rejected, revoked results cleared');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exit(1)});
