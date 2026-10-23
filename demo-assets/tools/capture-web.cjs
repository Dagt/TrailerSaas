// Asset tooling only. Use an existing loopback Vite server; never a production host.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const repo = path.resolve(__dirname, '../..');
const output = path.resolve(process.env.DEMO_OUTPUT || path.join(repo, 'demo-assets/review/web'));
const base = process.env.DEMO_BASE_URL || 'http://127.0.0.1:5350';
assert.ok(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(base).hostname), 'Loopback server required');
assert.ok(!fs.existsSync(output), 'Use a new output directory; existing assets must be preserved');
const git = (...args) => execFileSync('git', args, { cwd: repo, encoding: 'utf8' }).trim();
assert.equal(git('status', '--porcelain', '--', 'web'), '', 'Tracked Web source must be clean');
const id = n => `12000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const op = n => `1210${String(n).padStart(4, '0')}-0000-4000-8000-000000000001`;
const cu = n => `12200000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const primaryScenes = [
  ['admin-overview','admin','/admin'], ['admin-customers','admin','/admin/customers'],
  ['admin-equipment','admin','/admin/trailers'], ['admin-settings','admin','/admin/settings'],
  ['admin-audit','admin','/admin/audit/'+id(80)],
  ['operations-dispatch','operations','/operations'], ['operations-orders','operations','/operations/orders'],
  ['operations-trip','operations','/operations/trips/'+op(30)],
  ['operations-planning','operations','/operations/trips/new'],
  ['operations-trailer','operations','/operations/trailers/'+id(50)],
  ['operations-alerts','operations','/operations/alerts'],
  ['customer-dashboard','customer','/customer'], ['customer-orders','customer','/customer/orders'],
  ['customer-deliveries','customer','/customer/deliveries'], ['customer-documents','customer','/customer/documents'],
  ['customer-document-unavailable','customer','/customer/documents/'+cu(500)],
  ['customer-unknown-location','customer','/customer/trailers/'+cu(52)],
  ['operations-empty','operations','/operations/orders','empty'],
  ['customer-error','customer','/customer/orders','500'],
  ['customer-access-denied','customer','/customer/orders','403'],
  ['customer-loading','customer','/customer/orders','loading'],
  ['customer-mobile','customer','/customer', '',390,844],
  ['operations-tablet','operations','/operations', '',768,1024],
];
const scenes = process.env.DEMO_SCENES === 'details' ? [
  ['operations-trip-timeline','operations','/operations/trips/'+op(30)],
  ['operations-movement-history','operations','/operations/trailers/'+id(50)],
  ['operations-planning-review','operations','/operations/trips/new'],
  ['operations-trailer-search','operations','/operations/trailers'],
] : process.env.DEMO_SCENES === 'evidence' ? [
  ['admin-audit-changes','admin','/admin/audit/'+id(80)],
  ['customer-download-unavailable','customer','/customer/documents/'+cu(500)],
] : primaryScenes;
const report = { sourceRevision: git('rev-parse','HEAD'), webGitTree: git('rev-parse','HEAD:web'),
  capturedAt: new Date().toISOString(), environment: 'ISOLATED DEMO: actual React routes, synthetic reads, rejected writes, no Firebase or Backend authorization',
  browser: '', screenshots: [], pageErrors: [], checks: [] };
(async () => {
  fs.mkdirSync(output, {recursive:true});
  const browser = await chromium.launch({headless:true, ...(process.env.BROWSER_EXECUTABLE ? {executablePath:process.env.BROWSER_EXECUTABLE}: {})});
  report.browser = browser.version();
  try {
    const context = await browser.newContext({locale:'en-US',timezoneId:'America/Chicago',reducedMotion:'reduce'});
    // Prevent accidental external requests; existing fixtures do not need a network service.
    await context.route('**/*', r => new URL(r.request().url()).origin === new URL(base).origin ? r.continue() : r.abort());
    const page = await context.newPage();
    page.on('pageerror', e => report.pageErrors.push(e.message));
    for (const [name, area, route, state='', width=1440, height=900] of scenes) {
      await page.setViewportSize({width,height});
      const url = `${base}/dev/${area==='admin'?'tenant-admin':area}.html?${new URLSearchParams({route,state})}`;
      await page.goto(url);
      await page.locator('h1').first().waitFor();
      if(state==='loading') await page.locator('.ui-loading').first().waitFor();
      else await page.waitForFunction(() => !document.querySelector('.ui-loading'));
      await page.evaluate(() => document.fonts.ready);
      const actions = [];
      if(name === 'admin-audit-changes') {
        await page.getByRole('heading',{name:'Before / after comparison',exact:true}).evaluate(e=>e.scrollIntoView({block:'start'}));
        actions.push('Scroll actual before/after Audit comparison into view');
      }
      if(name === 'customer-download-unavailable') {
        await page.getByRole('button',{name:'Request authorized download',exact:true}).click();
        await page.getByText('Document storage is unavailable. No download link was issued.',{exact:true}).waitFor();
        await page.getByRole('heading',{name:'Secure file access',exact:true}).evaluate(e=>e.scrollIntoView({block:'start'}));
        actions.push('Request authorized download; fixture explicitly rejects with OBJECT_STORAGE_UNAVAILABLE; no grant or real file');
      }
      if(name === 'operations-trip-timeline') {
        await page.getByRole('button',{name:'Load more Trip events'}).click();
        await page.getByText('DEMO · Receiving instructions changed.',{exact:false}).waitFor();
        await page.getByRole('heading',{name:'Operational timeline',exact:true}).scrollIntoViewIfNeeded();
        actions.push('Load more Trip events; scroll actual timeline into view');
      }
      if(name === 'operations-movement-history') {
        await page.getByRole('heading',{name:'Recent movement timeline',exact:true}).scrollIntoViewIfNeeded();
        actions.push('Scroll actual movement timeline into view');
      }
      if(name === 'operations-planning-review') {
        await page.getByRole('checkbox',{name:'Select DEMO-ORD-104',exact:true}).check();
        for(const step of ['Stops','Resources','Review']) await page.getByRole('button',{name:'Continue to '+step,exact:true}).click();
        await page.getByRole('heading',{name:'Review Trip',exact:true}).waitFor();
        actions.push('Select eligible first Order; Continue to Stops / Resources / Review; no submission');
      }
      if(name === 'operations-trailer-search') {
        await page.getByLabel('Operational number',{exact:true}).fill('DEMO-TR-104');
        await page.getByRole('button',{name:'Search',exact:true}).click();
        await page.getByRole('link',{name:'DEMO-TR-104',exact:true}).waitFor();
        actions.push('Exact operational-number search DEMO-TR-104');
      }
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), name+' document overflow');
      assert.match(await page.locator('body').innerText(), /DEMO DATA|ISOLATED DEMO/);
      const png=await page.screenshot({path:path.join(output,name+'.png'),animations:'disabled'});
      report.screenshots.push({file:name+'.png',route,entry:`/dev/${area==='admin'?'tenant-admin':area}.html`,
        role:{admin:'TENANT_ADMIN',operations:'TRANSPORT_OPERATIONS',customer:'CUSTOMER_DISPATCHER'}[area]+' (synthetic)',
        state:state||'Synthetic read view', viewport:{width,height}, fullPage:false,
        sourceRevision:report.sourceRevision,webGitTree:report.webGitTree,
        actions, scroll:await page.evaluate(()=>({x:scrollX,y:scrollY})),
        dataSource:`web/src/dev/${area==='admin'?'tenant-admin':area}-fixtures.ts`,
        sha256:crypto.createHash('sha256').update(png).digest('hex'), capturedAt:new Date().toISOString(),
        authenticity:report.environment, captureMethod:'Unmodified browser PNG; no retouching or generated imagery'});
      report.checks.push({name:name+' visible demo disclosure and no document overflow',result:'PASS'});
      console.log('CAPTURED',name);
    }
    assert.equal(report.pageErrors.length,0,JSON.stringify(report.pageErrors));
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(output,'manifest.json'),JSON.stringify(report,null,2)+'\n');
  }
})().catch(e=>{console.error(e);process.exitCode=1});
