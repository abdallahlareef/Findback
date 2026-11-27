// Run with a Vite server at FINDBACK_UI_URL (default localhost:5178).
// Requires Playwright; PLAYWRIGHT_MODULE may point to an existing installation.
// Mocked API regression test: verifies frontend session integration, not backend authorization.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage();
    let user = null;
    let versions = [];
    const writes = [];
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route(url => url.pathname.startsWith('/api/'), async route => {
      const req = route.request();
      const path = new URL(req.url()).pathname;
      const reply = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
      if (path === '/api/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      if (req.method() === 'POST') {
        assert.equal(req.headers()['x-csrf-token'], 'test-token');
        writes.push(path);
      }
      if (path === '/api/auth/me') return reply(user || { message: 'Please sign in' }, user ? 200 : 401);
      if (path === '/api/auth/login') {
        const admin = req.postDataJSON().email.startsWith('staff');
        user = { id: admin ? 2 : 1, name: admin ? 'Test Staff' : 'Test Student', email: req.postDataJSON().email, role: admin ? 'ADMIN' : 'USER' };
        return reply(user);
      }
      if (path === '/api/auth/logout') { user = null; return route.fulfill({ status: 204 }); }
      if (!user) return reply({ message: 'Please sign in' }, 401);
      if (path === '/api/reports/mine') { assert.equal(user.role, 'USER'); return reply([{ id: 11, type: 'LOST', category: 'Earbuds', campusZone: 'Library', eventDate: '2026-11-20', colour: 'Black' }]); }
      if (path === '/api/evidence/reports/11/baseline') {
        versions = [{ id: 1, version: 1, provenance: 'INITIAL', createdAt: '2026-11-27T10:00:00Z', evidence: req.postDataJSON(), reason: null }];
        return reply(versions[0]);
      }
      if (path === '/api/evidence/reports/11') return reply(versions);
      if (path === '/api/v1/claims/staff/claims') return reply([]);
      if (path === '/api/evidence/staff/claims/7') return reply({ claimId: 7, claimant: { status: 'UnderReview', submissionsUsed: 1, pendingRequestId: null, versions }, finder: [], summary: { strength: 'Weak', distinctMatches: 0, serialOutcome: 'UNKNOWN', markOutcome: 'UNKNOWN', accessoryOutcome: 'UNKNOWN' }, history: [] });
      return reply([]);
    });
    const base = process.env.FINDBACK_UI_URL || 'http://127.0.0.1:5178';
    await page.goto(base + '/evidence');
    await page.waitForURL('**/login');
    async function login(email) {
      await page.getByLabel('Email address').fill(email);
      await page.getByLabel('Password', { exact: true }).fill('TestPassword123!');
      await page.getByRole('button', { name: 'Sign in', exact: true }).click();
      await page.getByRole('button', { name: 'Log out', exact: true }).waitFor();
    }
    await login('student@example.test');
    await page.getByRole('link', { name: 'Evidence', exact: true }).click();
    await page.getByRole('heading', { name: 'Your reports and baseline evidence' }).waitFor();
    assert.equal(await page.locator('input[type=password]').count(), 0);
    await page.getByRole('button', { name: 'Load my reports' }).click();
    await page.getByRole('button', { name: '#11 · LOST · Earbuds' }).click();
    await page.getByLabel('Private identifying mark', { exact: true }).fill('Synthetic star');
    await page.getByRole('button', { name: 'Save baseline' }).click();
    await page.getByRole('status').filter({ hasText: 'Evidence saved' }).waitFor();
    await page.reload();
    await page.getByRole('button', { name: 'Load my reports' }).waitFor();
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await page.waitForURL('**/login');
    assert.equal(await page.getByText('Synthetic star').count(), 0);
    await page.goto(base + '/evidence');
    await page.waitForURL('**/login');
    await login('staff@example.test');
    await page.getByRole('link', { name: 'Evidence review', exact: true }).click();
    await page.getByRole('heading', { name: 'Claim evidence review', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Load my reports' }).count(), 0);
    assert.equal(await page.getByRole('link', { name: 'Report Lost', exact: true }).count(), 0);
    await page.getByLabel('Claim number').fill('7');
    await page.getByRole('button', { name: 'Load claim evidence' }).click();
    await page.getByRole('heading', { name: 'Staff-only DOVE comparison' }).waitFor();
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await page.waitForURL('**/login');
    assert.equal(await page.getByRole('heading', { name: 'Staff-only DOVE comparison' }).count(), 0);
    assert(writes.includes('/api/evidence/reports/11/baseline'));
    assert.deepEqual(errors, []);
    console.log('PASS: shared login, protected evidence route, baseline + CSRF, reload, logout, account switch, staff navigation and DOVE view; no browser errors.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });


