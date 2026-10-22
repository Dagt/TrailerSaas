/* Optional real-browser regression; uses externally provided Playwright, no production import.
 * Run against the local Vite server, never a real tenant. Fixtures reject writes.
 * PLAYWRIGHT_MODULE / BROWSER_EXECUTABLE can point at an installed review runtime.
 */
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const repo = path.resolve(__dirname, '../..');
const git = (...args) => execFileSync('git', args, { cwd: repo, encoding: 'utf8' }).trim();
const output = path.resolve(process.env.UX124_OUTPUT || 'issue124-browser-results');
const base = process.env.UX124_BASE_URL || 'http://127.0.0.1:5340';
const crypto = require('node:crypto');
const report = { sourceRevision: git('rev-parse', 'HEAD'), webGitTree: git('rev-parse', 'HEAD:web'),
  environment: 'Actual React route components in explicit ISOLATED DEMO; synthetic read fixtures; no Firebase/Backend session; writes rejected',
  checks: [], screenshots: [], pageErrors: [], consoleErrors: [], accessibility: [] };
assert.equal(git('status', '--porcelain', '--untracked-files=no'), '', 'Commit the exact capture source first');
let page, route, role;
async function load(area, target) {
  route = target; role = area === 'operations' ? 'TRANSPORT_OPERATIONS (synthetic)' : 'CUSTOMER_DISPATCHER (synthetic)';
  await page.goto(base + '/dev/' + area + '.html?' + new URLSearchParams({ route }));
  await page.locator('h1').first().waitFor();
  await page.waitForFunction(() => !document.querySelector('.ui-loading'));
  await page.evaluate(() => document.fonts.ready);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, 'Document overflow');
}
async function screenshot(name, fullPage = false) {
  const file = name + '.png';
  const png = await page.screenshot({ path: path.join(output, file), animations: 'disabled', fullPage });
  report.screenshots.push({ file, sourceRevision: report.sourceRevision, webGitTree: report.webGitTree,
    route, role, viewport: page.viewportSize(), environment: report.environment,
    capturedAt: new Date().toISOString(), captureMethod: 'Native browser screenshot, unchanged pixels', fullPage,
    sha256: crypto.createHash('sha256').update(png).digest('hex') });
}
async function headingVisible(dialog) {
  const geometry = await dialog.evaluate(d => {
    const h = d.querySelector('h2').getBoundingClientRect(), r = d.getBoundingClientRect();
    return { scrollTop: d.scrollTop, headingTop: h.top, headingBottom: h.bottom, dialogTop: r.top, dialogBottom: r.bottom };
  });
  assert.ok(geometry.headingTop >= geometry.dialogTop, JSON.stringify(geometry));
  assert.ok(geometry.headingBottom <= geometry.dialogBottom, JSON.stringify(geometry));
  return geometry;
}
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {}) });
  try {
    const context = await browser.newContext({ locale: 'en-US', timezoneId: 'America/Mexico_City', reducedMotion: 'reduce' });
    page = await context.newPage();
    page.on('pageerror', e => report.pageErrors.push(e.message));
    page.on('console', e => { if (e.type() === 'error') report.consoleErrors.push(e.text()); });
    const cdp = await context.newCDPSession(page);
    for (const [width, height] of [[390,844],[320,844],[768,1024],[1280,800],[1440,900],[1920,1080],[720,450]]) {
      await page.setViewportSize({ width, height });
      await load('operations', '/operations/trips/12100030-0000-4000-8000-000000000001');
      const trigger = page.getByRole('button', { name: 'Cancel Trip', exact: true }); await trigger.click();
      const dialog = page.getByRole('dialog', { name: 'Start cancellation' }); await dialog.waitFor();
      const cancel = dialog.getByRole('button', { name: 'Cancel', exact: true });
      assert.equal(await cancel.evaluate(e => e === document.activeElement), true, 'Safe initial focus');
      const geometry = await headingVisible(dialog);
      const submit = dialog.getByRole('button', { name: 'Start cancellation', exact: true });
      assert.equal(await submit.isDisabled(), true, 'Physical confirmation remains mandatory');
      await screenshot(`cancel-trip-${width}x${height}`);
      // Walk backwards from safe Cancel through every form control. Native dialogs
      // may offer browser chrome after the last control; that is not a product trap.
      const fields = await dialog.locator('form input:not([disabled]), form select:not([disabled]), form textarea:not([disabled])').count();
      for (let i = 0; i < fields; i++) {
        await page.keyboard.press('Shift+Tab');
        const focus = await dialog.evaluate(d => {
          const e = document.activeElement, r = e.getBoundingClientRect(), h = d.querySelector('h2').getBoundingClientRect(), b = d.getBoundingClientRect();
          return { inside: d.contains(e), top: r.top, bottom: r.bottom, headingBottom: h.bottom, dialogBottom: b.bottom };
        });
        assert.ok(focus.inside && focus.top >= focus.headingBottom - 1 && focus.bottom <= focus.dialogBottom, JSON.stringify(focus));
        await headingVisible(dialog);
      }
      await dialog.getByLabel('I confirm these operational instructions.').check();
      await submit.click();
      await page.waitForFunction(() => document.querySelector('#action-reason')?.getAttribute('aria-invalid') === 'true');
      assert.equal(await dialog.getByLabel('Reason', { exact: true }).getAttribute('aria-describedby'), 'action-reason-error');
      await headingVisible(dialog);
      if (width === 390) await screenshot('cancel-trip-validation-390x844');
      await page.keyboard.press('Escape');
      assert.equal(await trigger.evaluate(e => e === document.activeElement), true, 'Escape focus restoration');
      await trigger.click(); await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click();
      assert.equal(await trigger.evaluate(e => e === document.activeElement), true, 'Cancel focus restoration');
      report.checks.push({ name: `M1 heading, Tab, confirmation, validation, Escape/Cancel focus ${width}x${height}`, result: 'PASS', geometry,
        ...(width === 720 ? { limitation: '720x450 equivalent reflow; not browser-chrome200% zoom' } : {}) });
    }
    for (const [width,height] of [[390,844],[768,1024],[1440,900]]) {
      await page.setViewportSize({ width,height });
      for (const target of ['/customer','/customer/orders','/customer/trailers','/customer/deliveries','/customer/documents']) {
        await load('customer', target);
        const labels = await page.locator('.status-badge').allTextContents();
        assert.ok(labels.length > 0 || target === '/customer/documents');
        assert.ok(labels.every(label => !/^[A-Z][A-Z_]+$/.test(label)), JSON.stringify(labels));
        for (const [name, label, value] of target.endsWith('orders') ? [['Order state','Partially delivered','PARTIALLY_DELIVERED']]
          : target.endsWith('trailers') ? [['Location state','Location unknown','LOCATION_UNKNOWN'],['Serviceability','Out of service','OUT_OF_SERVICE']]
          : target.endsWith('deliveries') ? [['Delivery state','En route','EN_ROUTE']]
          : target.endsWith('documents') ? [['Document type','Delivery photo','DELIVERY_PHOTO']] : []) {
          assert.equal(await page.getByRole('combobox',{name,exact:true}).getByRole('option',{name:label,exact:true}).getAttribute('value'), value);
        }
        const ax = await cdp.send('Accessibility.getFullAXTree');
        const unnamed = ax.nodes.filter(n => !n.ignored && ['button','link','textbox','combobox','checkbox'].includes(n.role?.value) && !n.name?.value?.trim());
        assert.equal(unnamed.length,0,'Unnamed actionable control');
        report.accessibility.push({ route: target, viewport: {width,height}, unnamedControls: 0, method: 'CDP names, not manual screen-reader certification' });
        await screenshot(target.replaceAll('/','-').slice(1)+`-${width}x${height}`, true);
        if (target.endsWith('orders')) {
          const select = page.getByRole('combobox',{name:'Order state',exact:true});
          await select.selectOption({label:'Partially delivered'});
          await page.waitForFunction(() => document.querySelector('select[aria-label="Order state"]')?.value === 'PARTIALLY_DELIVERED');
          assert.equal(await select.inputValue(),'PARTIALLY_DELIVERED');
          assert.ok((await select.getByRole('option',{selected:true}).allTextContents()).includes('Partially delivered'));
          await page.waitForFunction(() => !document.querySelector('.ui-loading'));
          await screenshot(`customer-orders-filtered-${width}x${height}`, true);
        }
        report.checks.push({name:`L1 readable labels, original filter values, no overflow, accessible names ${target} ${width}`,result:'PASS',labels});
      }
    }
    assert.equal(report.pageErrors.length,0,JSON.stringify(report.pageErrors));
    assert.equal(report.consoleErrors.length,0,JSON.stringify(report.consoleErrors));
    report.result='PASS';
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(output,'browser-review.json'),JSON.stringify(report,null,2));
  }
  console.log(JSON.stringify({result:report.result,checks:report.checks.length,images:report.screenshots.length,pageErrors:report.pageErrors.length,consoleErrors:report.consoleErrors.length}));
})().catch(error => { console.error(error); process.exitCode=1; });
