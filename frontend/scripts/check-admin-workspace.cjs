const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');const assert=require('node:assert/strict');
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
 for(const role of ['admin','researcher','doctor']){
 const page=await browser.newPage();let writes=0;
 await page.addInitScript(role=>localStorage.setItem('vis4srd-auth',JSON.stringify({state:{token:'fixture',isAuthenticated:true,user:{id:1,username:'fixture',role}},version:0})),role);
 await page.route(/^http:\/\/127\.0\.0\.1:5173\/api\//,route=>{if(route.request().method()!=='GET')writes++;const p=new URL(route.request().url()).pathname;const data=p==='/api/auth/me'?{id:1,username:'fixture',role}:p==='/api/me/doctor-applications'?[]:{items:[],total:0};return route.fulfill({json:{success:true,data}})});
 await page.goto('http://127.0.0.1:5173/admin-workspace');
 if(role!=='admin'){await page.waitForURL('**/home');assert.equal(await page.getByRole('heading',{name:'平台管理',exact:true}).count(),0);}
 else{await page.getByRole('heading',{name:'平台管理',exact:true}).waitFor();
 await page.getByRole('searchbox',{name:'搜索管理账号'}).fill('DEMO-A02');assert.equal(await page.getByRole('button',{name:'查看权限说明'}).count(),1);
 await page.getByRole('button',{name:'查看权限说明'}).click();await page.getByText('医生继承全部研究功能',{exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'角色变更与停用操作尚未接入'}).isDisabled(),true);
 await page.locator('.ant-drawer-close').click();await page.getByRole('tab',{name:'操作记录（演示）'}).click();await page.getByText('授予研究版本访问',{exact:true}).waitFor();assert.equal(writes,0);}
 await page.close();}
 console.log('PASS: admin-only route, account search, permission explanation, demo events, no writes');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exit(1)});
