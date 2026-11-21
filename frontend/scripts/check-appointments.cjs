const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
 const page=await browser.newPage();let writes=0;
 await page.addInitScript(()=>localStorage.setItem('vis4srd-auth',JSON.stringify({state:{token:'fixture',isAuthenticated:true,user:{id:1,username:'fixture',role:'doctor'}},version:0})));
 await page.route(/^http:\/\/127\.0\.0\.1:5173\/api\//,route=>{if(route.request().method()!=='GET')writes++;return route.fulfill({json:{success:true,data:{id:1,username:'fixture',role:'doctor'}}})});
 await page.goto('http://127.0.0.1:5173/appointments');
 await page.getByText('原型演示 · 全部为合成数据',{exact:true}).waitFor();
 await page.getByRole('button',{name:'查看预约'}).first().click();await page.getByRole('button',{name:'拒绝预约',exact:true}).click();
 assert.equal(await page.getByRole('button',{name:'确认处理'}).isDisabled(),true);
 await page.getByRole('textbox',{name:'预约处理说明'}).fill('演示：该时段无法接待');await page.getByRole('button',{name:'确认处理'}).click();
 await page.getByText('处理说明：演示：该时段无法接待',{exact:true}).waitFor();
 await page.locator('.ant-drawer-close').click();
 await page.getByRole('button',{name:'查看预约'}).nth(1).click();await page.getByRole('button',{name:'接受预约',exact:true}).click();await page.getByRole('button',{name:'确认处理'}).click();
 await page.getByText('此演示预约已处理，可通过“重置演示”重新体验。',{exact:true}).waitFor();
 assert.equal(writes,0);await page.reload();await page.getByText('待处理 2 条（演示）',{exact:true}).waitFor();
 console.log('PASS: rejection requires reason, acceptance, no writes, reload resets demo');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exit(1)});
