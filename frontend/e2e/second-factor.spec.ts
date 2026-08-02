import { type Locator, type Page, expect, test } from '@playwright/test';

import { nextCode } from './totp';

const username = process.env['GLACIER_E2E_MFA_USERNAME'];
const password = process.env['GLACIER_E2E_MFA_PASSWORD'];
const adminUsername = process.env['GLACIER_E2E_ADMIN_USERNAME'];
const adminPassword = process.env['GLACIER_E2E_ADMIN_PASSWORD'];

/** The shipped default of the step-up grace window, which the last phase borrows and gives back. */
const DEFAULT_GRACE_MINUTES = 5;

test.skip(
  !username || !password || !adminUsername || !adminPassword,
  'Set GLACIER_E2E_MFA_USERNAME/PASSWORD on an account reserved for this spec, and '
    + 'GLACIER_E2E_ADMIN_USERNAME/PASSWORD on an administrator.',
);

test('a user enrolls a second factor, signs in with it, and turns it off again', async ({ page }) => {
  test.setTimeout(240_000);
  page.on('dialog', (dialog) => dialog.accept());

  const card = page.locator('app-two-factor-card');
  const signIn = async (as = username!, secret = password!) => {
    await page.goto('/login');
    await page.getByLabel('Username or email').fill(as);
    await page.getByLabel('Password').fill(secret);
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

  const { secret, step, recoveryCodes } = await enroll(card);
  expect(recoveryCodes).toHaveLength(10);

  // Sign in through the second stage.
  await signOut();
  await signIn();
  await expect(page.locator('#mfa-code')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
  const login = await nextCode(secret, step);
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
  // The recovery-code sign-in above opened the step-up grace window, so the password alone suffices:
  // were the window closed, the card would stay on this step and ask for a one-time code instead.
  await expect(card.getByLabel('One-time code')).toHaveCount(0);
  await expect(card.getByText('Not set up')).toBeVisible();

  await signOut();
  await signIn();
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await expect(page.locator('#mfa-code')).toHaveCount(0);

  // With the grace window shut, the same operation asks for a code on top of the password.
  await signOut();
  await signIn(adminUsername!, adminPassword!);
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await setStepUpGrace(page, 0);
  await signOut();

  await signIn();
  // Without this the next navigation aborts the login request still in flight.
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await openSettings();
  const reEnrollment = await enroll(card);
  await card.getByRole('button', { name: 'Turn off' }).click();
  await card.getByLabel('Current password').fill(password!);
  await card.getByRole('button', { name: 'Continue' }).click();
  const stepUpCode = card.getByLabel('One-time code');
  await expect(stepUpCode).toBeVisible();

  await stepUpCode.fill((await nextCode(reEnrollment.secret, reEnrollment.step)).code);
  await card.getByRole('button', { name: 'Continue' }).click();
  await expect(card.getByText('Not set up')).toBeVisible();
});

/**
 * The tunable lives on the instance-wide singleton, so leaving it at zero would carry into the other
 * browser project's run of this same spec.
 */
test.afterEach(async ({ page }) => {
  if (!adminUsername || !adminPassword) return;
  // Whoever the test left signed in — or nobody, if it failed mid-flight.
  await page.goto('/settings');
  const signOut = page.getByRole('button', { name: 'Sign out' });
  if (await signOut.isVisible()) await signOut.click();

  await page.goto('/login');
  await page.getByLabel('Username or email').fill(adminUsername);
  await page.getByLabel('Password').fill(adminPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.locator('app-notes-shell')).toBeVisible();
  await setStepUpGrace(page, DEFAULT_GRACE_MINUTES);
});

async function setStepUpGrace(page: Page, minutes: number): Promise<void> {
  await page.goto('/admin/settings');
  const field = page.getByLabel('Second-factor re-prompt grace period (minutes)');
  await expect(field).toBeVisible();
  await field.fill(String(minutes));
  await page.getByRole('button', { name: 'Save settings' }).click();
  await expect(page.getByText('Instance settings saved.')).toBeVisible();
}

/** Password, then the key the QR encodes, then a code derived from it. */
async function enroll(
  card: Locator,
): Promise<{ secret: string; step: number; recoveryCodes: string[] }> {
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

  const done = card.getByRole('button', { name: 'Done' });
  await expect(done).toBeDisabled();
  await card.getByLabel('I have saved these recovery codes.').check();
  await done.click();
  await expect(card.getByText('Active', { exact: true })).toBeVisible();
  await expect(card.getByText('10 recovery codes left.')).toBeVisible();
  return { secret, step: enrollment.step, recoveryCodes };
}

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
