import { type Locator, expect, test } from '@playwright/test';

import { nextCode } from './totp';

const username = process.env['GLACIER_E2E_MFA_USERNAME'];
const password = process.env['GLACIER_E2E_MFA_PASSWORD'];

test.skip(
  !username || !password,
  'Set GLACIER_E2E_MFA_USERNAME and GLACIER_E2E_MFA_PASSWORD on an account reserved for this spec.',
);

test('a user enrolls a second factor, signs in with it, and turns it off again', async ({ page }) => {
  test.setTimeout(180_000);
  page.on('dialog', (dialog) => dialog.accept());

  const card = page.locator('app-two-factor-card');
  const signIn = async () => {
    await page.goto('/login');
    await page.getByLabel('Username or email').fill(username!);
    await page.getByLabel('Password').fill(password!);
    await page.getByRole('button', { name: 'Sign in' }).click();
  };
  const openSettings = async () => {
    await page.goto('/settings');
    await expect(card.getByRole('heading', { name: 'Two-factor authentication' })).toBeVisible();
  };
  // The notes shell hides the application header, so sign out from an account page.
  const signOut = async () => {
    await openSettings();
    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);
  };

  await signIn();
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await openSettings();
  await discardUnconfirmedEnrollment(card);

  // Enroll: password, then the key the QR encodes, then a code derived from it.
  await card.getByRole('button', { name: 'Set up' }).click();
  await card.getByLabel('Current password').fill(password!);
  await card.getByRole('button', { name: 'Continue' }).click();
  await expect(card.locator('svg.qr')).toBeVisible();

  await card.getByText('Cannot scan?').click();
  const secret = (await card.locator('code.secret').innerText()).trim();
  expect(secret.length).toBeGreaterThan(0);

  const enrollment = await nextCode(secret);
  await card.getByLabel('Code from your app').fill(enrollment.code);
  await card.getByRole('button', { name: 'Confirm' }).click();

  await expect(card.getByText('Save your recovery codes')).toBeVisible();
  const recoveryCodes = await card.locator('ul.codes code').allInnerTexts();
  expect(recoveryCodes).toHaveLength(10);

  const done = card.getByRole('button', { name: 'Done' });
  await expect(done).toBeDisabled();
  await card.getByLabel('I have saved these recovery codes.').check();
  await done.click();
  await expect(card.getByText('Active', { exact: true })).toBeVisible();
  await expect(card.getByText('10 recovery codes left.')).toBeVisible();

  // Sign in through the second stage.
  await signOut();
  await signIn();
  await expect(page.locator('#mfa-code')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
  const login = await nextCode(secret, enrollment.step);
  await page.getByLabel('Authentication code').fill(login.code);
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.locator('app-notes-shell')).toBeVisible();

  // A wrong code keeps the stage; a recovery code gets through instead.
  await signOut();
  await signIn();
  await page.getByLabel('Authentication code').fill('000000');
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.getByRole('alert')).toContainText('not correct');
  await expect(page.locator('#mfa-code')).toHaveValue('');

  await page.getByRole('button', { name: 'Use a recovery code instead' }).click();
  await page.getByLabel('Recovery code').fill(recoveryCodes[0]);
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.locator('app-notes-shell')).toBeVisible();

  // Turn it off; the next sign-in is single-step again.
  await openSettings();
  await expect(card.getByText('9 recovery codes left.')).toBeVisible();
  await card.getByRole('button', { name: 'Turn off' }).click();
  await card.getByLabel('Current password').fill(password!);
  await card.getByRole('button', { name: 'Continue' }).click();
  await expect(card.getByText('Not set up')).toBeVisible();

  await signOut();
  await signIn();
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await expect(page.locator('#mfa-code')).toHaveCount(0);
});

/**
 * The account is shared by the two browser projects and by retries, so an earlier attempt may have
 * left an unconfirmed enrollment behind. A confirmed one cannot be cleaned up here — the password
 * alone would not have got us this far.
 */
async function discardUnconfirmedEnrollment(card: Locator): Promise<void> {
  const discard = card.getByRole('button', { name: 'Discard and start over' });
  if (await discard.isVisible()) await discard.click();
  await expect(card.getByText('Not set up')).toBeVisible();
}
