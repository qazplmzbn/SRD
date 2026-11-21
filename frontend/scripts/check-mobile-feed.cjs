const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');const assert=require('node:assert/strict');const fs=require('node:fs');
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:5173/mobile/');await page.getByRole('button',{name:'排版演示',exact:true}).click();await page.locator('.feed-card').first().waitFor();assert.equal(await page.locator('.feed-card').count(),4);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.getByRole('button',{name:'查看微博配图1',exact:true}).click();await page.getByText('配图预览 · 合成插画',{exact:true}).waitFor();await page.locator('.ant-modal-close:visible').click();
 await page.getByRole('button',{name:'知乎',exact:true}).click();assert.equal(await page.locator('.feed-card').count(),1);await page.getByRole('button',{name:'查看详情',exact:true}).click();await page.getByText(/没有真实原帖链接或互动数据/).waitFor();await page.locator('.ant-modal-close:visible').click();
 await page.getByRole('button',{name:'抖音',exact:true}).click();await page.getByText('仅封面 · 无视频文件',{exact:true}).waitFor();
 await page.getByRole('button',{name:'全部',exact:true}).click();await page.locator('main').evaluate(el=>el.scrollTop=0);
 fs.mkdirSync('output/mobile-qa',{recursive:true});await page.screenshot({path:'output/mobile-qa/feed.png',animations:'disabled'});
 assert.deepEqual(errors,[]);console.log('PASS: four card types, platform filter, image preview, detail, video unavailable state, mobile width');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exit(1)});
