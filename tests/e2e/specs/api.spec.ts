import { test, expect, APIRequestContext } from '@playwright/test';
import { createHmac } from 'node:crypto';

const password = 'TestPath!2026Secure';
const uid = () => `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

async function mutate(api: APIRequestContext, method: string, path: string, data?: unknown) {
  // Fetch after each session boundary: login rotates session identity and the CSRF token.
  const csrf = await api.get('/api/auth/csrf');
  expect(csrf.ok()).toBeTruthy();
  const token = await csrf.json();
  return api.fetch(`/api${path}`, { method, data, headers: { [token.headerName]: token.token } });
}

async function newUser(api: APIRequestContext) {
  const email = `e2e-${uid()}@example.com`;
  const registered = await mutate(api, 'POST', '/auth/register', {
    firstName: 'Taylor', lastName: 'Test', email, password,
  });
  expect(registered.ok(), await registered.text()).toBeTruthy();
  const login = await mutate(api, 'POST', '/auth/login', { email, password });
  expect(login.ok(), await login.text()).toBeTruthy();
  return email;
}

test('ledger edits and deletion preserve exact balances and reject overdrafts', async ({ request }) => {
  await newUser(request);
  const created = await mutate(request, 'POST', '/accounts', { name: 'Retirement reserve', type: 'ROTH_IRA', openingBalance: 1000.10 });
  expect(created.ok(), await created.text()).toBeTruthy();
  const account = await created.json();
  const payload = { accountId: account.id, type: 'CONTRIBUTION', amount: 125.25, description: 'Monthly deposit', date: new Date().toISOString().slice(0, 10) };
  const added = await mutate(request, 'POST', '/transactions', payload);
  expect(added.ok(), await added.text()).toBeTruthy();
  const transaction = await added.json();
  let accounts = await (await request.get('/api/accounts')).json();
  expect(Number(accounts.find((a: { id: number }) => a.id === account.id).balance)).toBeCloseTo(1125.35, 2);
  const updated = await mutate(request, 'PUT', `/transactions/${transaction.id}`, { ...payload, amount: 150.50 });
  expect(updated.ok(), await updated.text()).toBeTruthy();
  accounts = await (await request.get('/api/accounts')).json();
  expect(Number(accounts.find((a: { id: number }) => a.id === account.id).balance)).toBeCloseTo(1150.60, 2);
  const overdraft = await mutate(request, 'POST', '/transactions', { ...payload, type: 'WITHDRAWAL', amount: 9000 });
  expect(overdraft.status()).toBeGreaterThanOrEqual(400);
  const removed = await mutate(request, 'DELETE', `/transactions/${transaction.id}`);
  expect(removed.ok(), await removed.text()).toBeTruthy();
  const dashboard = await (await request.get('/api/dashboard')).json();
  expect(Number(dashboard.totalBalance)).toBeCloseTo(1000.10, 2);
  expect(Number(dashboard.annualContributions)).toBe(0);
});

test('resource ownership, role checks, CSRF, and beneficiary validation', async ({ request, playwright, baseURL }) => {
  await newUser(request);
  const created = await mutate(request, 'POST', '/accounts', { name: 'Owner only', type: 'TRADITIONAL_IRA', openingBalance: 500 });
  const account = await created.json();
  const second = await playwright.request.newContext({ baseURL });
  try {
    await newUser(second);
    const foreignEdit = await mutate(second, 'PUT', `/accounts/${account.id}`, { name: 'Intrusion', type: 'ROTH_IRA' });
    expect([403, 404]).toContain(foreignEdit.status());
    const foreignTransaction = await mutate(second, 'POST', '/transactions', { accountId: account.id, type: 'CONTRIBUTION', amount: 10, description: 'Forbidden', date: new Date().toISOString().slice(0, 10) });
    expect([403, 404]).toContain(foreignTransaction.status());
    expect((await second.get('/api/audit')).status()).toBe(403);
  } finally { await second.dispose(); }
  const missingCsrf = await request.post('/api/accounts', { data: { name: 'Unsafe', type: 'ROTH_IRA', openingBalance: 0 } });
  expect(missingCsrf.status()).toBe(403);
  const invalid = await mutate(request, 'PUT', `/accounts/${account.id}/beneficiaries`, { beneficiaries: [{ name: 'Jordan', relationship: 'Spouse', percentage: 75 }] });
  expect(invalid.status()).toBe(422);
  const valid = await mutate(request, 'PUT', `/accounts/${account.id}/beneficiaries`, { beneficiaries: [{ name: 'Jordan', relationship: 'Spouse', percentage: 100 }] });
  expect(valid.ok(), await valid.text()).toBeTruthy();
});

test('goals, zero-return calculator, and transaction pagination use live PostgreSQL', async ({ request }) => {
  await newUser(request);
  const calculated = await mutate(request, 'POST', '/goals/calculate', { currentAge: 64, retirementAge: 65, currentSavings: 1000, monthlyContribution: 100, annualReturn: 0, targetAmount: 2200 });
  expect(calculated.ok(), await calculated.text()).toBeTruthy();
  const result = await calculated.json();
  expect(Number(result.projectedBalance)).toBeCloseTo(2200, 2);
  expect(Number(result.investmentGrowth)).toBeCloseTo(0, 2);
  const goal = await mutate(request, 'POST', '/goals', { name: 'Test retirement', targetAmount: 500000, currentAmount: 1000, targetDate: '2045-12-31', monthlyContribution: 400, expectedReturn: 6 });
  expect(goal.ok(), await goal.text()).toBeTruthy();
  const saved = await goal.json();
  expect((await mutate(request, 'DELETE', `/goals/${saved.id}`)).ok()).toBeTruthy();
  const paged = await request.get('/api/transactions?page=0&size=5&sort=date,desc&search=absent');
  expect(paged.ok()).toBeTruthy();
  const page = await paged.json();
  expect(page.content).toEqual([]);
  expect(page.totalElements).toBe(0);
  expect(page.size).toBe(5);
});

function authenticatorCode(secret: string, step: number) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const character of secret) bits += alphabet.indexOf(character).toString(2).padStart(5, '0');
  const key = Buffer.from(bits.match(/.{8}/g)!.map(byte => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac('sha1', key).update(counter).digest();
  const offset = hash[19] & 15;
  return ((hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).toString().padStart(6, '0');
}

test('MFA enrollment, login challenge, replay rejection, and administrator audit access', async ({ request }) => {
  const email = await newUser(request);
  const setup = await mutate(request, 'POST', '/auth/mfa/setup', { password });
  expect(setup.ok(), await setup.text()).toBeTruthy();
  const { secret } = await setup.json();
  const remainder = Date.now() % 30_000;
  if (remainder > 24_000) await new Promise(resolve => setTimeout(resolve, 30_000 - remainder + 100));
  const step = Math.floor(Date.now() / 30_000);
  // Adjacent valid steps exercise replay controls without adding a 30-second sleep to the suite.
  const enrolled = await mutate(request, 'POST', '/auth/mfa/enable', { password, code: authenticatorCode(secret, step - 1) });
  expect(enrolled.ok(), await enrolled.text()).toBeTruthy();
  expect((await enrolled.json()).mfaEnabled).toBe(true);
  expect((await mutate(request, 'POST', '/auth/logout')).ok()).toBeTruthy();
  expect((await request.get('/api/auth/me')).status()).toBe(401);
  const challenge = await mutate(request, 'POST', '/auth/login', { email, password });
  expect(challenge.status()).toBe(401);
  expect((await challenge.json()).code).toBe('MFA_REQUIRED');
  const signedIn = await mutate(request, 'POST', '/auth/login', { email, password, code: authenticatorCode(secret, step) });
  expect(signedIn.ok(), await signedIn.text()).toBeTruthy();
  const replay = await mutate(request, 'POST', '/auth/mfa/disable', { password, code: authenticatorCode(secret, step) });
  expect(replay.status()).toBe(401);
  const disabled = await mutate(request, 'POST', '/auth/mfa/disable', { password, code: authenticatorCode(secret, step + 1) });
  expect(disabled.ok(), await disabled.text()).toBeTruthy();
  expect((await disabled.json()).mfaEnabled).toBe(false);
  await mutate(request, 'POST', '/auth/logout');
  const admin = await mutate(request, 'POST', '/auth/login', { email: 'admin@monelytics.dev', password: 'AdminPath!2026' });
  expect(admin.ok(), await admin.text()).toBeTruthy();
  const audit = await request.get('/api/audit?page=0&size=10');
  expect(audit.ok()).toBeTruthy();
  expect((await audit.json()).content.length).toBeGreaterThan(0);
});
