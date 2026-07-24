import { expect, test } from '@playwright/test';

const username = process.env['GLACIER_E2E_USERNAME'];
const password = process.env['GLACIER_E2E_PASSWORD'];

test.skip(!username || !password, 'Set GLACIER_E2E_USERNAME and GLACIER_E2E_PASSWORD.');

test('a user completes the non-image core note workflow', async ({ page }, testInfo) => {
  test.setTimeout(60_000);

  const suffix = `${testInfo.project.name}-${Date.now()}`;
  const notebookName = `M6 ${suffix}`;
  const labelName = `Label ${suffix}`;
  const textTitle = `Text ${suffix}`;
  const checklistTitle = `Checklist ${suffix}`;
  const tablet = testInfo.project.name === 'tablet';
  const openNavigation = async () => {
    if (!tablet) return;
    await page.getByRole('button', { name: 'Open navigation' }).click();
    await expect(page.locator('.sidebar')).toHaveClass(/sidebar--open/);
  };

  await page.goto('/login');
  await page.getByLabel('Username or email').fill(username!);
  await page.getByLabel('Password').fill(password!);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await page.reload();
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await expect(page).toHaveURL(/\/notes(?:\/|$)/);

  await openNavigation();
  await page.getByRole('button', { name: 'New notebook' }).click();
  await page.getByLabel('New notebook name').fill(notebookName);
  await page.getByLabel('New notebook name').press('Enter');
  await page.getByRole('button', { name: new RegExp(notebookName) }).click();

  await openNavigation();
  await page.getByRole('button', { name: 'New label' }).click();
  await page.getByLabel('New label name').fill(labelName);
  await page.getByLabel('New label name').press('Enter');
  await expect(page.getByRole('button', { name: labelName })).toBeVisible();
  if (tablet) await page.getByRole('button', { name: 'Open navigation' }).click();

  await page.getByRole('button', { name: 'Text note' }).click();
  await page.getByLabel('Note title').fill(textTitle);
  await page.getByLabel('Note content').fill('# Heading\n\n**Bold** and a [safe link](https://example.com).');
  await page
    .locator('app-note-editor details')
    .filter({ hasText: 'Labels' })
    .locator('summary')
    .click();
  await page.getByLabel(labelName).check();
  await expect(page.getByText('Saved', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Save and close' }).click();
  await expect(page.getByText(textTitle, { exact: true })).toBeVisible();

  const textCard = page.locator('app-note-card').filter({ hasText: textTitle });
  await textCard.getByRole('button', { name: 'Pin' }).click();
  await expect(page.getByText('Pinned', { exact: true })).toBeVisible();
  await textCard.getByRole('button', { name: 'Archive' }).click();
  await openNavigation();
  await page.getByRole('link', { name: 'Archive' }).click();
  await expect(page.getByText(textTitle, { exact: true })).toBeVisible();

  await openNavigation();
  await page.getByRole('button', { name: new RegExp(notebookName) }).click();
  await page.getByRole('button', { name: 'Checklist' }).click();
  await page.getByLabel('Note title').fill(checklistTitle);
  await page.getByRole('button', { name: 'Add item' }).click();
  await page.getByLabel('Checklist item').fill('First item');
  await expect(page.getByText('Saved', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Save and close' }).click();
  const checklistCard = page.locator('app-note-card').filter({ hasText: checklistTitle });
  await checklistCard.getByRole('checkbox').check();
  await expect(checklistCard.getByRole('checkbox')).toBeChecked();

  await checklistCard.getByRole('button', { name: 'Move to trash' }).click();
  await openNavigation();
  await page.getByRole('link', { name: 'Trash' }).click();
  const trashedCard = page.locator('app-note-card').filter({ hasText: checklistTitle });
  await expect(trashedCard).toBeVisible();
  await trashedCard.getByRole('button', { name: 'Restore' }).click();
  await expect(trashedCard).not.toBeVisible();

  const themeToggle = page.getByRole('button', { name: /Switch to (light|dark) theme/ });
  const themeAction = await themeToggle.getAttribute('aria-label');
  await themeToggle.click();
  await expect(page.locator('html')).toHaveClass(
    themeAction === 'Switch to light theme' ? /theme-light/ : /theme-dark/,
  );

  if (tablet) {
    await openNavigation();
    await expect(page.locator('.sidebar')).toHaveClass(/sidebar--open/);
  }
});
