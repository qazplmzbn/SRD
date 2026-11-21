const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const base = process.env.WORKSPACE_TEST_URL || 'http://127.0.0.1:5173';

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage();
    page.on('pageerror', e => console.error(e));
    page.setDefaultTimeout(8000);
    const user = { id: 5, username: 'fixture', role: 'doctor' };
    const recordRequests = [];
    await page.addInitScript(user => localStorage.setItem('vis4srd-auth', JSON.stringify({ state: { token: 'fixture', isAuthenticated: true, user }, version: 0 })), user);
    await page.route(`${base}/api/**`, route => {
      const path = new URL(route.request().url()).pathname;
      const booking = { state: 'ACCEPTED', shared: true, scheduledAt: '2026-11-21T08:00:00Z', name: '同名用户' };
      if (path.endsWith('/records')) recordRequests.push(path);
      const data = path === '/api/auth/me' ? user : path === '/api/care/appointments' ? [
        { ...booking, id: 'first', patientId: 41 }, { ...booking, id: 'second', patientId: 41 },
        { ...booking, id: 'third', patientId: 42 }, { ...booking, id: 'cancelled', patientId: 43, state: 'CANCELLED' },
      ] : { posts: [{ text: '合成共享贴文' }], results: [{ name: '合成量表', score: 3 }] };
      return route.fulfill({ json: { success: true, data: path === '/api/datasets' ? [] : path === '/api/scale/tasks' ? { tasks: [], stats: {} } : data } });
    });
    await page.goto(`${base}/home`);
    await page.getByRole('button', { name: '专业服务', exact: true }).click();
    await page.getByRole('menuitem', { name: '用户资料', exact: true }).click();
    await page.getByRole('heading', { name: '用户资料', exact: true }).waitFor();
    await page.getByRole('combobox', { name: '选择用户' }).click();
    assert.equal(await page.getByText(/^同名用户（账号 \d+）$/).count(), 2);
    await page.getByText('同名用户（账号 41）', { exact: true }).click();
    assert.deepEqual(recordRequests, []);
    await page.getByRole('combobox', { name: '选择预约' }).click();
    await page.getByText(/· first$/).click();
    await page.getByText('合成共享贴文', { exact: true }).waitFor();
    await page.getByRole('tab', { name: '量表结果 (1)' }).click();
    await page.getByText('合成量表', { exact: true }).waitFor();
    assert.deepEqual(recordRequests, ['/api/care/appointments/first/records']);
    await page.goto(`${base}/appointments`);
    await page.getByRole('tab',{name:'已关闭'}).click();
    await page.getByText('已取消',{exact:true}).waitFor();
    assert.equal(await page.getByRole('link',{name:'查看资料'}).count(),0);
    await page.getByRole('tab',{name:'已接受'}).click();
    await page.getByRole('row').filter({hasText:'账号 42'}).getByRole('link',{name:'查看资料'}).click();
    await page.getByText('合成共享贴文',{exact:true}).waitFor();
    assert.equal(recordRequests.at(-1),'/api/care/appointments/third/records');
    await page.goto(`${base}/scale`);
    await page.waitForURL('**/care-users');
    console.log('PASS: real account grouping, cancelled booking excluded, records fetched only for selected appointment');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });