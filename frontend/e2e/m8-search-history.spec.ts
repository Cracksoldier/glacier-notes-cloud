import { expect, test, type Page } from '@playwright/test';

const username = process.env['GLACIER_E2E_USERNAME'];
const password = process.env['GLACIER_E2E_PASSWORD'];

test.skip(!username || !password, 'Set GLACIER_E2E_USERNAME and GLACIER_E2E_PASSWORD.');

async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username or email').fill(username!);
  await page.getByLabel('Password').fill(password!);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.locator('app-notes-shell')).toBeVisible();
}

test('search, history, and two-tab conflict recovery work together', async ({ context, page }) => {
  test.setTimeout(60_000);
  const marker = `aurora${Date.now()}`;
  const originalTitle = `History ${marker}`;
  const serverTitle = `Server ${marker}`;
  const localTitle = `Local ${marker}`;

  await login(page);
  await page.getByRole('button', { name: 'Text note' }).click();
  await page.getByLabel('Note title').fill(originalTitle);
  await page.getByLabel('Note content').fill(`Searchable ${marker}`);
  await expect(page.getByText('Saved', { exact: true })).toBeVisible();
  const refreshedNotes = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      response.request().method() === 'GET' &&
      url.pathname === '/api/v1/notes' &&
      response.ok()
    );
  });
  await page.getByRole('button', { name: 'Save and close' }).click();
  await Promise.all([expect(page.locator('app-note-editor')).not.toBeVisible(), refreshedNotes]);

  await page.getByPlaceholder('Search notes…').fill(marker);
  await expect(page.getByRole('heading', { name: `Search: ${marker}` })).toBeVisible();
  const searchResult = page
    .getByRole('article')
    .filter({ has: page.getByText(originalTitle, { exact: true }) });
  await expect(searchResult).toBeVisible();
  await searchResult.getByRole('button', { name: 'Open note' }).click();

  const secondTab = await context.newPage();
  await secondTab.goto(page.url());
  await expect(secondTab.locator('app-notes-shell')).toBeVisible();
  await secondTab.getByPlaceholder('Search notes…').fill(marker);
  await expect(secondTab.getByRole('heading', { name: `Search: ${marker}` })).toBeVisible();
  const secondSearchResult = secondTab
    .getByRole('article')
    .filter({ has: secondTab.getByText(originalTitle, { exact: true }) });
  await expect(secondSearchResult).toBeVisible();
  await secondSearchResult.getByRole('button', { name: 'Open note' }).click();

  await page.getByLabel('Note title').fill(serverTitle);
  await expect(page.getByText('Saved', { exact: true })).toBeVisible();
  await secondTab.getByLabel('Note title').fill(localTitle);
  await expect(secondTab.getByText('Another session changed this note.')).toBeVisible();
  await expect(secondTab.getByRole('button', { name: 'Copy local draft' })).toBeVisible();
  await expect(secondTab.getByRole('button', { name: 'Overwrite with draft' })).toBeVisible();

  await secondTab.getByRole('button', { name: 'Reload server' }).click();
  await expect(secondTab.getByLabel('Note title')).toHaveValue(serverTitle);
  await secondTab.getByRole('button', { name: 'History' }).click();
  await expect(secondTab.getByText('Version history')).toBeVisible();
  await expect(secondTab.getByText('CONFLICT')).toBeVisible();
  await secondTab.locator('.history-panel__version').filter({ hasText: originalTitle }).click();
  await expect(secondTab.locator('.history-preview h2')).toHaveText(originalTitle);

  secondTab.once('dialog', (dialog) => dialog.accept());
  await secondTab.getByRole('button', { name: 'Restore this version' }).click();
  await expect(secondTab.getByLabel('Note title')).toHaveValue(originalTitle);
});
