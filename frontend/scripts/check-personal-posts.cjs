// Run with PLAYWRIGHT_MODULE pointing to an available Playwright installation.
// UI checks use synthetic responses; backend behavior is tested separately in Java.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://127.0.0.1:5173/my-posts');
    await page.waitForURL('**/login');
    await context.addInitScript(() => localStorage.setItem('vis4srd-auth', JSON.stringify({
      state: { isAuthenticated: true, token: 'ui-test-only', user: { id: 101, username: 'ui_test', role: 'user' } }, version: 0,
    })));
    let posts = [], importCalls = 0;
    await page.route(/\/api\/me\/posts(?:[/?]|$)/, async route => {
      const request = route.request();
      if (request.method() === 'POST') {
        importCalls++;
        const body = request.postDataJSON();
        assert.equal(body.consent, true);
        assert.equal(body.posts.length, 1);
        posts = body.posts.map((p, i) => ({ ...p, id: `fixture-${i}`, importedAt: '2026-11-13T12:00:00Z' }));
        return route.fulfill({ json: { success: true, data: { imported: posts.length, skipped: 0 } } });
      }
      if (request.method() === 'DELETE') {
        posts = [];
        return route.fulfill({ json: { success: true } });
      }
      return route.fulfill({ json: { success: true, data: { items: posts, total: posts.length, page: 1, pageSize: 10 } } });
    });
    await page.goto('http://127.0.0.1:5173/my-posts');
    await page.getByText('还没有保存贴文，可以先添加一条').waitFor();
    await page.getByLabel('贴文内容', { exact: true }).fill('测试记录：今天完成了一项学习任务。');
    await page.getByRole('button', { name: '预览这条贴文' }).click();
    assert.equal(importCalls, 0);
    assert.equal(await page.getByRole('button', { name: '确认保存所选贴文' }).isDisabled(), true);
    await page.getByRole('button', { name: '取消预览' }).click();
    assert.equal(importCalls, 0);
    await page.getByLabel('导入贴文文件').setInputFiles({ name: 'posts.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify([
      { content: '今天完成了一项学习任务，想记录下来。', source: '我的记录', publishedAt: null },
      { content: '这条不上传。', source: '我的记录', publishedAt: null },
    ])) });
    await page.getByRole('checkbox', { name: '选择第2条' }).uncheck();
    await page.getByRole('checkbox', { name: '我确认所选内容' }).check();
    assert.equal(importCalls, 0);
    fs.mkdirSync('../output/personal-posts-qa', { recursive: true });
    await page.screenshot({ path: '../output/personal-posts-qa/mobile-preview.png', fullPage: true });
    await page.getByRole('button', { name: '确认保存所选贴文' }).click();
    await page.getByRole('button', { name: /^删\s*除$/ }).waitFor();
    assert.equal(importCalls, 1);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.screenshot({ path: '../output/personal-posts-qa/desktop-saved.png', fullPage: true });
    await page.getByRole('button', { name: /^删\s*除$/ }).click();
    await page.getByRole('button', { name: /^取\s*消$/ }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    assert.equal(posts.length, 1);
    await page.getByRole('button', { name: /^删\s*除$/ }).click();
    await page.getByRole('dialog').getByRole('button', { name: /^删\s*除$/ }).click();
    await page.getByText('还没有保存贴文，可以先添加一条').waitFor();
    assert.equal(posts.length, 0);
    assert.deepEqual(errors, []);
    console.log('PASS: login guard, local preview, consent, selection, save, mobile overflow, delete confirmation, no JS errors');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exit(1); });

