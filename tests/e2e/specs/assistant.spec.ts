import { expect, test, APIRequestContext } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

async function post(api: APIRequestContext, path: string, data: unknown) {
  const token = await (await api.get("/api/auth/csrf")).json();
  const response = await api.post(`/api${path}`, {
    data,
    headers: { [token.headerName]: token.token },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

test("assistant evaluates actual affordability and never creates transactions", async ({
  page,
}) => {
  const today = new Date().toISOString().slice(0, 10);
  const month = today.slice(0, 7);
  await post(page.request, "/auth/register", {
    firstName: "Casey",
    lastName: "Morgan",
    email: `assistant-${Date.now()}@example.com`,
    password: "BrowserTest!2026",
  });
  const account = await post(page.request, "/accounts", {
    name: "Assistant checking",
    type: "CHECKING",
    openingBalance: 1000,
  });
  const categories = await (await page.request.get("/api/categories")).json();
  const groceries = categories.find(
    (category: { name: string }) => category.name === "Groceries",
  );
  const salary = categories.find(
    (category: { name: string }) => category.name === "Salary",
  );
  await post(page.request, "/transactions", {
    accountId: account.id,
    type: "INCOME",
    amount: 6000,
    description: "Recorded salary",
    date: today,
    categoryId: salary.id,
  });
  await post(page.request, "/transactions", {
    accountId: account.id,
    type: "EXPENSE",
    amount: 500,
    description: "Recorded groceries",
    date: today,
    categoryId: groceries.id,
  });
  await post(page.request, "/budgets", {
    categoryId: groceries.id,
    month,
    limitAmount: 1500,
  });
  const ledgerBefore = await (
    await page.request.get("/api/transactions")
  ).json();
  const accountsBefore = await (await page.request.get("/api/accounts")).json();
  await page.goto("/assistant");
  await expect(
    page.getByRole("heading", { name: "Ask your financial assistant" }),
  ).toBeVisible();
  await expect(
    page
      .getByRole("status")
      .filter({ hasText: "Loading your expense categories" }),
  ).not.toBeVisible();
  await page
    .getByRole("button", { name: "Ask assistant", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("Enter a question");
  await page
    .getByLabel("Your question", { exact: true })
    .fill("Could this grocery purchase fit my budget?");
  await page
    .getByLabel("Purchase amount ($, optional)", { exact: true })
    .fill("200");
  await page
    .getByLabel(/^Purchase category/)
    .selectOption({ label: "Groceries" });
  await page
    .getByRole("button", { name: "Ask assistant", exact: true })
    .click();
  await expect(page.locator(".assistant-decision")).toContainText(
    "Appears to fit your recorded plan",
  );
  await expect(page.locator(".assistant-reply")).toContainText(
    "Demo assistant",
  );
  await expect(
    page
      .locator(".assistant-factor")
      .filter({ hasText: "Recorded monthly income" }),
  ).toContainText("$6,000.00");
  await expect(
    page
      .locator(".assistant-factor")
      .filter({ hasText: "Applicable budget room" }),
  ).toContainText("$1,000.00");
  await expect(
    page
      .locator(".assistant-factor")
      .filter({ hasText: "Cushion after purchase" }),
  ).toContainText("$800.00");
  await expect(page.locator(".assistant-disclaimer")).toContainText(
    "Educational information only",
  );
  await expect(page.locator(".assistant-reply-foot")).toContainText(
    "no transactions performed",
  );
  await expect(page.locator(".assistant-reply button")).toHaveCount(0);
  const accessibility = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(
    accessibility.violations.map((violation) => ({
      id: violation.id,
      targets: violation.nodes.map((node) => node.target),
    })),
  ).toEqual([]);
  await page
    .getByLabel("Purchase amount ($, optional)", { exact: true })
    .fill("1200");
  await page
    .getByRole("button", { name: "Ask assistant", exact: true })
    .click();
  await expect(page.locator(".assistant-decision")).toContainText(
    "Beyond your recorded spending room",
  );
  const ledgerAfter = await (
    await page.request.get("/api/transactions")
  ).json();
  const accountsAfter = await (await page.request.get("/api/accounts")).json();
  expect(ledgerAfter.totalElements).toBe(ledgerBefore.totalElements);
  expect(accountsAfter).toEqual(accountsBefore);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth,
    ),
  ).toBe(false);
  const mobile = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(
    mobile.violations.map((violation) => ({
      id: violation.id,
      targets: violation.nodes.map((node) => node.target),
    })),
  ).toEqual([]);
  await page.getByRole("button", { name: "Toggle navigation" }).click();
  await expect(
    page.getByRole("link", { name: "Assistant", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Reports", exact: true }),
  ).toBeVisible();
});
