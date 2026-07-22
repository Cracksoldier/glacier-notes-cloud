import path from 'node:path';

import { expect, test, type Page } from '@playwright/test';

const username = process.env['GLACIER_E2E_USERNAME'];
const password = process.env['GLACIER_E2E_PASSWORD'];

test.skip(!username || !password, 'Set GLACIER_E2E_USERNAME and GLACIER_E2E_PASSWORD.');

async function openTransfer(page: Page, tablet: boolean): Promise<void> {
  if (tablet) await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('button', { name: /Import \/ Export/ }).click();
  await expect(page.getByRole('dialog', { name: 'Import / Export' })).toBeVisible();
}

test('portable fixture import and full export complete through the notes UI', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  const tablet = testInfo.project.name === 'tablet';
  await page.goto('/login');
  await page.getByLabel('Username or email').fill(username!);
  await page.getByLabel('Password').fill(password!);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.locator('app-notes-shell')).toBeVisible();

  await openTransfer(page, tablet);
  await page.locator('input[type="file"]').setInputFiles(
    path.resolve('../compatibility-fixtures/desktop-schema-v1/note.glacier.json'),
  );
  const complete = page.getByRole('heading', { name: 'Import complete' });
  const conflicts = page.getByRole('heading', { name: 'Import conflicts' });
  await expect(complete.or(conflicts)).toBeVisible({ timeout: 30_000 });
  if (await conflicts.isVisible()) {
    await page.getByRole('button', { name: /Add as copies/ }).click();
    await expect(complete).toBeVisible({ timeout: 30_000 });
  }
  await page.getByRole('button', { name: 'Done' }).click();

  await openTransfer(page, tablet);
  const download = page.waitForEvent('download', { timeout: 30_000 });
  await page.getByRole('button', { name: 'Export', exact: true }).click();
  await expect((await download).suggestedFilename()).toMatch(/\.glacier\.json$/);
});
