import { test, expect, Page, APIRequestContext } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resolve } from 'node:path';
import { mkdirSync, readFileSync } from 'node:fs';

const screenshots = resolve('../../docs/screenshots');
mkdirSync(screenshots, { recursive: true });
const today = () => new Date().toISOString().slice(0, 10);
async function accessible(page: Page) {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(result.violations.map(({ id, nodes }) => ({ id, nodes: nodes.map(({ target, failureSummary }) => ({ target, failureSummary })) }))).toEqual([]);
}
async function mutate(api: APIRequestContext, path: string, data: unknown) {
  const token = await (await api.get('/api/auth/csrf')).json();
  const response = await api.post(`/api${path}`, { data, headers: { [token.headerName]: token.token } });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}
async function register(api: APIRequestContext) {
  const email = `ui-${Date.now()}-${Math.random().toString(36).slice(2)}@example.com`;
  await mutate(api, '/auth/register', { firstName: 'Taylor', lastName: 'Morgan', email, password: 'BrowserTest!2026' });
  return email;
}

test('registration, account creation, expense editing, CSV, deletion and logout', async ({ page }, info) => {
  await page.goto('/register');
  await page.getByRole('button', { name: 'Create your account' }).click();
  await expect(page.getByText('Enter your first name.')).toBeVisible();
  await page.getByLabel('First name').fill('Taylor');
  await page.getByLabel('Last name').fill('Morgan');
  await page.getByLabel('Email address').fill(`browser-${Date.now()}@example.com`);
  await page.getByLabel(/^Password/).fill('BrowserTest!2026');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Create your account' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: /Taylor/ })).toBeVisible();
  await page.goto('/accounts');
  const opener = page.getByRole('button', { name: 'Add account', exact: true });
  await opener.click();
  await accessible(page);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(opener).toBeFocused();
  await opener.click();
  await page.getByLabel('Account name').fill('Browser checking');
  await page.getByLabel(/^Account type/).selectOption('CHECKING');
  await page.getByLabel('Opening balance ($)').fill('1000.10');
  await page.getByRole('button', { name: 'Save account', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Browser checking' })).toBeVisible();
  await page.goto('/transactions');
  await page.getByRole('button', { name: 'Add transaction', exact: true }).click();
  await page.getByLabel('Amount ($)').fill('125.25');
  await page.getByRole('dialog').getByLabel(/^Category/).selectOption({ label: 'Groceries' });
  await page.getByRole('textbox', { name: 'Description', exact: true }).fill('Browser grocery expense');
  await accessible(page);
  await page.getByRole('button', { name: 'Save transaction', exact: true }).click();
  await expect(page.getByRole('cell', { name: '-$125.25', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Edit Browser grocery expense' }).click();
  await page.getByLabel('Amount ($)').fill('150.50');
  await page.getByRole('button', { name: 'Save transaction', exact: true }).click();
  await expect(page.getByRole('cell', { name: '-$150.50', exact: true })).toBeVisible();
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Export CSV', exact: true }).click();
  const download = await downloadPromise;
  const csvPath = info.outputPath('transactions.csv');
  await download.saveAs(csvPath);
  expect(readFileSync(csvPath, 'utf8')).toContain('Browser grocery expense');
  await page.getByLabel('Search transactions').fill('no matching activity');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText('Nothing here just yet')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters', exact: true }).click();
  await page.getByRole('button', { name: 'Delete Browser grocery expense' }).click();
  await page.getByRole('button', { name: 'Remove transaction', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'Browser grocery expense', exact: true })).not.toBeVisible();
  await page.goto('/dashboard');
  await expect(page.locator('.balance-amount')).toHaveText('$1,000.10');
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/accounts');
  await expect(page).toHaveURL(/\/login/);
});

test('overall/category budgets, overspending, recurring payment and savings contributions', async ({ page }) => {
  await register(page.request);
  const account = await mutate(page.request, '/accounts', { name: 'Planning checking', type: 'CHECKING', openingBalance: 1000 });
  const categories = await (await page.request.get('/api/categories')).json();
  const grocery = categories.find((category: { name: string }) => category.name === 'Groceries');
  await mutate(page.request, '/transactions', { accountId: account.id, categoryId: grocery.id, type: 'EXPENSE', amount: 125, description: 'Budget groceries', date: today() });
  await page.goto('/budgets');
  await page.getByRole('button', { name: 'Create budget', exact: true }).click();
  await page.getByLabel('Expense category').selectOption({ label: 'Groceries' });
  await page.getByLabel('Budget amount ($)').fill('100');
  await page.getByRole('button', { name: 'Save budget', exact: true }).click();
  await expect(page.getByText('Over budget', { exact: true })).toBeVisible();
  await accessible(page);
  await page.getByRole('button', { name: 'Create budget', exact: true }).click();
  await page.getByLabel('Expense category').selectOption('');
  await page.getByLabel('Budget amount ($)').fill('500');
  await page.getByRole('button', { name: 'Save budget', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Overall budget' })).toBeVisible();
  await page.goto('/bills');
  await page.getByRole('button', { name: 'Add recurring item', exact: true }).click();
  await page.getByLabel('Name', { exact: true }).fill('Browser subscription');
  await page.getByLabel(/^Kind/).selectOption('SUBSCRIPTION');
  await page.getByLabel('Amount ($)').fill('20');
  await page.getByLabel('Expense category').selectOption({ label: 'Subscriptions' });
  await page.getByLabel('Next due date').fill(today());
  await page.getByRole('button', { name: 'Save recurring item', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Browser subscription' })).toBeVisible();
  await page.getByRole('button', { name: 'Record payment', exact: true }).click();
  await accessible(page);
  await page.getByRole('dialog').getByRole('button', { name: 'Record payment', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.goto('/transactions');
  await expect(page.getByRole('cell', { name: '-$20.00', exact: true })).toBeVisible();
  await page.goto('/goals');
  await page.getByRole('button', { name: 'Create a goal', exact: true }).click();
  const goalForm = page.getByRole('dialog');
  await goalForm.getByLabel('Goal name').fill('Browser emergency fund');
  await goalForm.getByLabel('Target amount ($)').fill('1000');
  await goalForm.getByLabel('Saved so far ($)').fill('0');
  await goalForm.getByLabel(/^Target date/).fill('2030-12-31');
  await goalForm.getByRole('button', { name: 'Save goal' }).click();
  await page.getByRole('button', { name: 'Add savings contribution', exact: true }).click();
  await page.getByLabel('Savings contribution ($)').fill('250');
  await page.getByLabel('Note (optional)').fill('Earmarked for emergencies');
  await page.getByRole('button', { name: 'Record savings contribution', exact: true }).click();
  await expect(page.locator('.contribution-history')).toContainText('$250.00');
  await accessible(page);
  await page.keyboard.press('Escape');
  await page.goto('/dashboard');
  await expect(page.locator('.balance-amount')).toHaveText('$855.00');
  await page.getByRole('button', { name: 'Notifications', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your notifications' })).toBeVisible();
  await expect(page.getByText('Budget limit reached', { exact: true })).toBeVisible();
  await accessible(page);
});

test('demo pages, real charts, desktop and mobile are accessible', async ({ page }) => {
  test.setTimeout(240_000);
  await page.goto('/login');
  await page.getByRole('button', { name: /Fill demo credentials/ }).click();
  await page.getByRole('button', { name: 'Sign in to Monelytics' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('.balance-amount')).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 1050 });
  await expect(page.locator('.brand-mark')).toHaveCSS('width', '43px');
  await page.screenshot({ path: resolve(screenshots, 'dashboard-desktop.png'), fullPage: true });
  for (const route of ['dashboard', 'accounts', 'transactions', 'budgets', 'bills', 'goals', 'reports', 'categories', 'assistant', 'settings']) {
    await page.goto(`/${route}`);
    await expect(page.locator('main h1')).toBeVisible();
    await expect(page.locator('ml-state .spinner')).not.toBeVisible();
    await accessible(page);
  }
  await page.goto('/dashboard');
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.balance-amount')).toBeVisible();
  await page.screenshot({ path: resolve(screenshots, 'dashboard-mobile.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false);
  await page.getByRole('button', { name: 'Toggle navigation' }).click();
  await page.getByRole('link', { name: 'Transactions', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your transactions.' })).toBeVisible();
  await expect(page.locator('tbody tr').first()).toBeVisible();
  await accessible(page);
});

test('password recovery sends local email, resets once and signs in with the new password', async ({ page, playwright }) => {
  const email = await register(page.request);
  await page.goto('/forgot-password');
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Send recovery link' }).click();
  await expect(page.getByRole('status')).toContainText('If that email belongs to an account');
  await accessible(page);
  const mailbox = await playwright.request.newContext({ baseURL: process.env['E2E_MAIL_URL'] || 'http://localhost:8025' });
  try {
    let id = '';
    await expect.poll(async () => {
      const result = await (await mailbox.get('/api/v1/messages')).json();
      const message = result.messages.find((item: { To: { Address: string }[] }) => item.To.some(recipient => recipient.Address === email));
      id = message?.ID ?? '';
      return Boolean(id);
    }).toBe(true);
    const message = await (await mailbox.get(`/api/v1/message/${id}`)).json();
    const link = message.Text.match(/https?:\/\/[^\s]+\/reset-password\?token=[A-Za-z0-9_-]{43}/)?.[0];
    expect(link).toBeTruthy();
    const token = new URL(link).searchParams.get('token');
    await page.goto(`/reset-password?token=${token}`);
    await page.getByLabel(/^New password/).fill('RecoveredPassword!2026');
    await page.getByLabel('Confirm new password').fill('RecoveredPassword!2026');
    await page.getByRole('button', { name: 'Reset password', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Your password was reset');
    await page.getByRole('link', { name: 'Return to sign in' }).click();
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel(/^Password/).fill('RecoveredPassword!2026');
    await page.getByRole('button', { name: 'Sign in to Monelytics' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    const csrf = await (await page.request.get('/api/auth/csrf')).json();
    const replay = await page.request.post('/api/auth/reset-password', { data: { token, password: 'AnotherPassword!2026' }, headers: { [csrf.headerName]: csrf.token } });
    expect(replay.status()).toBe(422);
  } finally { await mailbox.dispose(); }
});

test('landing and administrator audit interface are accessible', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.topbar')).toHaveCSS('display', 'flex');
  await page.screenshot({ path: resolve(screenshots, 'landing.png'), fullPage: true });
  await accessible(page);
  await page.getByRole('link', { name: 'Sign in', exact: true }).click();
  await page.getByLabel('Email address').fill('admin@monelytics.dev');
  await page.getByLabel(/^Password/).fill('AdminDemo!2026');
  await page.getByRole('button', { name: 'Sign in to Monelytics' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'The audit log.' })).toBeVisible();
  await expect(page.locator('tbody tr').first()).toBeVisible();
  await accessible(page);
});
