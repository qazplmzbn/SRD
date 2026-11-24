const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const base = process.env.WORKSPACE_TEST_URL || 'http://127.0.0.1:5173';
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    for (const role of ['user', 'doctor', 'researcher', 'admin']) {
      const context = await browser.newContext();
      await context.addInitScript(role => localStorage.setItem('vis4srd-auth', JSON.stringify({ state: {
        token: 'workspace-ui-fixture', isAuthenticated: true, user: { id: 101, username: 'fixture', nickname: '测试用户', avatar_color: '#123456', role },
      }, version: 0 })), role);
      const page = await context.newPage();
      page.on('pageerror', e => console.error('PAGE ERROR', e.message));
      let researchCalls = 0;
      await page.route(`${base}/api/**`, route => {
        if (/\/(users|models|risk|datasets|home)(\/|\?|$)/.test(route.request().url())) researchCalls++;
        const path = new URL(route.request().url()).pathname;
        const data = path === '/api/auth/me' ? { id: 101, username: 'fixture', nickname: '测试用户', role } : path === '/api/datasets' ? [] : path === '/api/scale/tasks' ? { tasks: [], stats: { total: 0, completed: 0 } } : { items: [], total: 0 };
        return route.fulfill({ json: { success: true, data } });
      });
      await page.goto(`${base}/home`);
      assert.equal(await page.getByRole('button', { name: '模型中心', exact: true }).count(), 0);
      if (role === 'user') {
        await page.getByText('此账号没有专业工作台访问权限').waitFor();
        assert.equal(await page.getByRole('button', { name: '心理档案', exact: true }).count(), 0);
        for (const path of ['archive', 'risk', 'model-center']) {
          await page.goto(`${base}/${path}`);
          await page.waitForURL('**/home');
        }
        assert.equal(researchCalls, 0, 'Denied routes must not load research data');
      } else {
        const nav = page.getByRole('navigation', { name: '专业主导航' });
        await nav.getByRole('button', { name: '研究工作台' }).click();
        await page.getByRole('menuitem', { name: '研究数据', exact: true }).waitFor();
        assert.equal(await page.getByRole('menuitem', { name: '风险检测', exact: true }).count(), 1);
        assert.equal(await nav.getByRole('button', { name: '工具中心' }).count(), 0);
        assert.equal(await page.getByRole('menuitem', { name: '批量实验' }).count(), 0);
        if (role === 'admin') {
          await nav.getByRole('link',{name:'返回管理后台'}).click();
          await page.getByRole('navigation',{name:'管理员主导航'}).getByRole('link',{name:'查看系统'}).waitFor();
        } else {
          await nav.getByRole('button',{name:'专业服务'}).click();
          await page.getByRole('menuitem',{name:role==='doctor'?'预约与用户资料':'医生认证',exact:true}).waitFor();
          if(role==='doctor'){ await page.goto(`${base}/certification`); await page.waitForURL('**/appointments'); }
          await page.goto(`${base}/model-config`);
          await page.waitForURL('**/home');
        }
      }
      await context.close();
    }
    console.log('PASS: shared professional homepage, inherited doctor menus, user route guard, configuration restricted');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exit(1); });
