import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { resolve } from 'node:path';

async function assertAccessible(page: Page) {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(result.violations.map(({ id, nodes }) => ({
    id, nodes: nodes.map(({ target, failureSummary }) => ({ target, failureSummary })),
  }))).toEqual([]);
}

test('registration, IRA creation, beneficiaries, transaction editing and deletion', async ({ page }) => {
  await page.goto('/register');
  await page.getByRole('button', { name: 'Create your account' }).click();
  await expect(page.getByText('Enter your first name.')).toBeVisible();
  await page.getByLabel('First name').fill('Taylor');
  await page.getByLabel('Last name').fill('Morgan');
  await page.getByLabel('Email address').fill(`ui-${Date.now()}@example.com`);
  await page.getByLabel(/^Password/).fill('BrowserPath!2026');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Create your account' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: /Taylor/ })).toBeVisible();
  await page.getByRole('link', { name: 'Accounts', exact: true }).click();
  const addAccount = page.getByRole('button', { name: 'Add IRA account', exact: true });
  await addAccount.click();
  const accountDialog = page.getByRole('dialog');
  await expect(accountDialog).toBeVisible();
  await assertAccessible(page);
  await page.keyboard.press('Escape');
  await expect(accountDialog).not.toBeVisible();
  await expect(addAccount).toBeFocused();
  await addAccount.click();
  await page.getByLabel('Account name').fill('Browser Roth IRA');
  await page.getByLabel('Opening balance ($)').fill('1000.10');
  await page.getByRole('button', { name: 'Save account', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Browser Roth IRA' })).toBeVisible();
  await page.getByRole('button', { name: /Manage/ }).click();
  await page.getByLabel('Name', { exact: true }).fill('Jordan Morgan');
  await page.getByLabel('Relationship').fill('Spouse');
  await page.getByRole('button', { name: 'Save beneficiaries' }).click();
  await expect(page.getByText('Jordan Morgan', { exact: true })).toBeVisible();
  await assertAccessible(page);
  await page.getByRole('link', { name: 'Transactions', exact: true }).click();
  await page.getByRole('button', { name: 'Add transaction', exact: true }).click();
  await page.getByLabel('Amount ($)').fill('125.25');
  await page.getByRole('textbox', { name: 'Description', exact: true }).fill('Browser contribution');
  await assertAccessible(page);
  await page.getByRole('button', { name: 'Save transaction', exact: true }).click();
  await expect(page.getByRole('cell', { name: '+$125.25', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Edit Browser contribution' }).click();
  await page.getByLabel('Amount ($)').fill('150.50');
  await page.getByRole('button', { name: 'Save transaction', exact: true }).click();
  await expect(page.getByRole('cell', { name: '+$150.50', exact: true })).toBeVisible();
  await page.getByLabel('Search transactions').fill('no matching activity');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText('Nothing here just yet')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters', exact: true }).click();
  await page.getByRole('button', { name: 'Delete Browser contribution' }).click();
  await page.getByRole('button', { name: 'Remove transaction', exact: true }).click();
  await expect(page.getByText('Your transaction was removed and the account balance is updated.')).toBeVisible();
  await page.getByRole('link', { name: 'Overview', exact: true }).click();
  await expect(page.locator('.balance-amount')).toHaveText('$1,000.10');
  await page.getByRole('link', { name: 'Goals', exact: true }).click();
  await page.getByLabel('Current age', { exact: true }).fill('64');
  await page.getByLabel('Retirement age', { exact: true }).fill('65');
  await page.getByLabel('Current savings ($)', { exact: true }).fill('1000');
  await page.getByLabel('Monthly contribution ($)', { exact: true }).fill('100');
  await page.getByLabel('Annual return (%)', { exact: true }).fill('0');
  await page.getByLabel('Target balance ($)', { exact: true }).fill('2200');
  await page.getByRole('button', { name: 'Explore my projection' }).click();
  await expect(page.locator('.projection-top strong')).toHaveText('$2,200');
  await assertAccessible(page);
  await page.getByRole('button', { name: 'Create a goal', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Goal name').fill('Retirement freedom');
  await dialog.getByLabel(/^Target date/).fill('2045-12-31');
  await dialog.getByRole('button', { name: 'Save goal', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Retirement freedom' })).toBeVisible();
  await page.getByRole('link', { name: 'Settings', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Save profile' })).toBeVisible();
  await assertAccessible(page);
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/accounts');
  await expect(page).toHaveURL(/\/login/);
});

test('demo dashboard is accessible at desktop and mobile sizes', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /Fill demo credentials/ }).click();
  await page.getByRole('button', { name: 'Sign in to Monelytics' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('.balance-amount')).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 1050 });
  // Verify the production stylesheet loads under the actual Nginx CSP.
  await expect(page.locator('.brand-mark')).toHaveCSS('width', '43px');
  await expect(page.locator('.balance-amount')).toHaveCSS('font-size', '48px');
  await page.screenshot({ path: resolve('../../docs/screenshots/dashboard-desktop.png'), fullPage: true });
  await assertAccessible(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: resolve('../../docs/screenshots/dashboard-mobile.png'), fullPage: true });
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(overflow).toBe(false);
  await page.getByRole('button', { name: 'Toggle navigation' }).click();
  await page.getByRole('link', { name: 'Transactions', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your transactions.' })).toBeVisible();
  await expect(page.locator('tbody tr').first()).toBeVisible();
  await assertAccessible(page);
});

test('public landing page has no accessibility violations', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.topbar')).toHaveCSS('display', 'flex');
  await page.screenshot({ path: resolve('../../docs/screenshots/landing.png'), fullPage: true });
  await assertAccessible(page);
  await page.getByRole('link', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('administrator can review the audit trail in the protected UI', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('admin@monelytics.dev');
  await page.getByLabel(/^Password/).fill('AdminPath!2026');
  await page.getByRole('button', { name: 'Sign in to Monelytics' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.getByRole('link', { name: 'Audit log', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'The audit log.' })).toBeVisible();
  await expect(page.locator('tbody tr').first()).toBeVisible();
  await assertAccessible(page);
});
