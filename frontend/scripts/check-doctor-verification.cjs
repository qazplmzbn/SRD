const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const output = '../output/verification-qa'; fs.mkdirSync(output, { recursive: true });
  try {
    let applications = [], doctor = false, submitted = 0;
    const errors = [];
    async function open(role) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
      await context.addInitScript(role => localStorage.setItem('vis4srd-auth', JSON.stringify({state:{token:'verification-ui-test',isAuthenticated:true,user:{id:role==='admin'?102:101,username:'fixture',nickname:role==='admin'?'审核管理员':'测试研究者',role}},version:0})), role);
      const page=await context.newPage(); page.on('pageerror',e=>errors.push(e.message));
      await page.route(/^http:\/\/127\.0\.0\.1:5173\/api\//, async route => {
        const req=route.request(), url=new URL(req.url()); let data={};
        if(url.pathname==='/api/auth/me') data={id:role==='admin'?102:101,username:'fixture',nickname:role==='admin'?'审核管理员':'测试研究者',role:role==='admin'?'admin':doctor?'doctor':'researcher'};
        if(url.pathname==='/api/me/doctor-applications') {
          if(req.method()==='POST') {
            assert.ok(req.headers()['content-type'].includes('multipart/form-data')); submitted++;
            applications=[{id:'application-fixture',applicantId:101,realName:'测试姓名',institution:'测试机构',qualification:'测试资质',credentialNumber:'TEST-001',notes:'仅演示',status:'PENDING',submittedAt:'2026-11-14T06:00:00Z',reviewedAt:null,reviewReason:null}]; data={id:applications[0].id};
          } else data=applications;
        }
        if(url.pathname==='/api/admin/doctor-applications') {
          const filtered=applications.filter(x=>url.searchParams.get('status')==='ALL'||x.status===url.searchParams.get('status'));
          data={items:filtered,total:filtered.length};
        }
        if(url.pathname.endsWith('/review')) {
          const body=req.postDataJSON(); assert.ok(body.reason.trim());
          applications[0].status={APPROVE:'APPROVED',REJECT:'REJECTED',REVOKE:'REVOKED'}[body.decision];
          applications[0].reviewReason=body.reason; doctor=body.decision==='APPROVE';
        }
        return route.fulfill({json:{success:true,data}});
      });
      return {page,context};
    }
    const user=await open('researcher'), page=user.page;
    await page.goto('http://127.0.0.1:5173/home');
    await page.getByRole('heading',{name:'专业工作台',exact:true}).waitFor();
    assert.equal(await page.getByRole('button',{name:'心理档案',exact:true}).count(),0);
    await page.screenshot({path:`${output}/workspace-desktop.png`,fullPage:true,animations:'disabled'});
    await page.getByRole('link',{name:/申请医生认证/}).click();
    await page.getByLabel('真实姓名',{exact:true}).fill('测试姓名');
    await page.getByLabel('执业或工作机构',{exact:true}).fill('测试机构');
    await page.getByLabel('资质名称',{exact:true}).fill('测试资质');
    await page.getByLabel('证书或执业编号',{exact:true}).fill('TEST-001');
    await page.getByLabel('证明材料',{exact:true}).setInputFiles({name:'proof.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 synthetic only')});
    assert.equal(submitted,0);
    assert.equal(await page.getByRole('button',{name:'提交认证申请'}).isDisabled(),true);
    await page.screenshot({path:`${output}/certification-desktop.png`,fullPage:true,animations:'disabled'});
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.screenshot({path:`${output}/certification-mobile.png`,fullPage:true,animations:'disabled'});
    await page.getByRole('checkbox',{name:/我确认提交内容真实/}).scrollIntoViewIfNeeded();
    await page.screenshot({path:`${output}/certification-mobile-submit.png`,fullPage:true,animations:'disabled'});
    await page.getByRole('checkbox',{name:/我确认提交内容真实/}).check();
    await page.getByRole('button',{name:'提交认证申请'}).click();
    await page.getByText('申请正在审核，无需重复提交',{exact:true}).waitFor(); assert.equal(submitted,1);
    const admin=await open('admin');
    await admin.page.goto('http://127.0.0.1:5173/verification-review');
    await admin.page.getByRole('button',{name:'查看申请'}).click();
    await admin.page.getByText('TEST-001',{exact:true}).waitFor();
    await admin.page.screenshot({path:`${output}/review-desktop.png`,fullPage:true,animations:'disabled'});
    await admin.page.getByRole('button',{name:'审核通过',exact:true}).click();
    assert.equal(await admin.page.getByRole('button',{name:'保存审核结果'}).isDisabled(),true);
    await admin.page.getByLabel('核查结论或处理原因').fill('测试核查结论');
    await admin.page.getByRole('checkbox',{name:'我已核查材料并确认本次处理结果'}).check();
    await admin.page.getByRole('button',{name:'保存审核结果'}).click();
    await admin.page.getByText('暂无此状态的认证申请').waitFor();
    await page.getByRole('button',{name:'刷新状态'}).click();
    await page.getByText('认证已通过，可以继续使用全部研究功能',{exact:true}).waitFor();
    assert.equal(doctor,true); assert.deepEqual(errors,[]);
    await page.getByRole('link',{name:/返回专业工作台/}).waitFor();
    doctor=false; applications[0].status='REJECTED';applications[0].reviewReason='请补充清晰材料';
    await page.getByRole('button',{name:'刷新状态'}).click();
    await page.getByText('申请未通过，请按反馈修改后重新提交',{exact:true}).waitFor();
    await page.getByRole('button',{name:'复用上次填写信息'}).click();
    assert.equal(await page.getByLabel('真实姓名',{exact:true}).inputValue(),'测试姓名');
    assert.equal(await page.getByRole('button',{name:'提交认证申请'}).isDisabled(),true);
    assert.equal(await page.getByRole('checkbox',{name:/我确认提交内容真实/}).isChecked(),false);
    await user.context.close(); await admin.context.close();
    console.log('PASS: top navigation, no legacy sidebar, desktop/mobile layout, consent-gated multipart application, admin review, refreshed doctor identity, no JS errors');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1)});
