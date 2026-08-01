import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthStore } from '../core/auth.store';
import { MfaStatusStatusEnum } from '../shared/generated-api/model/mfaStatus';
import { provideApi } from '../shared/generated-api/provide-api';
import { AccountSettingsComponent } from './account-settings.component';

const profile = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'member',
  email: 'member@example.com',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-07-24T00:00:00Z',
  emailChangeAvailable: true,
  selfDeletionEnabled: true,
};

const settings = {
  theme: 'dark',
  language: 'en',
  moveCheckedToBottom: false,
  trashAutoPurgeDays: 30,
  trashAutoPurgeMayBeDisabled: false,
};

describe('AccountSettingsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountSettingsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
        provideRouter([]),
        { provide: AuthStore, useValue: { session: () => null, clear: vi.fn() } },
      ],
    }).compileComponents();
  });

  /** The profile and settings loads are chained through promises, so each one lands a tick later. */
  async function settle(url: string, body: Record<string, unknown>): Promise<void> {
    const http = TestBed.inject(HttpTestingController);
    for (let attempt = 0; attempt < 20; attempt++) {
      const matched = http.match(url);
      if (matched.length) {
        matched[0].flush(body);
        return;
      }
      await Promise.resolve();
    }
    throw new Error(`no request was made to ${url}`);
  }

  async function render() {
    const fixture = TestBed.createComponent(AccountSettingsComponent);
    fixture.detectChanges();
    await settle('/api/v1/me/profile', profile);
    await settle('/api/v1/me/settings', settings);
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    await settle('/api/v1/me/mfa', { status: MfaStatusStatusEnum.None, available: false });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function codeField(fixture: { nativeElement: HTMLElement }): HTMLInputElement | null {
    return fixture.nativeElement.querySelector('input[autocomplete="one-time-code"]');
  }

  it('leaves no code field behind when an email change is accepted straight away', async () => {
    const fixture = await render();
    const http = TestBed.inject(HttpTestingController);
    const component = fixture.componentInstance;
    component.newEmail = 'new@example.com';
    component.emailPassword = 'correct-horse-battery-staple';

    const pending = component.requestEmailChange();
    const request = http.expectOne('/api/v1/me/email-change');
    expect(request.request.body).toEqual({
      currentPassword: 'correct-horse-battery-staple',
      newEmail: 'new@example.com',
      code: undefined,
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await pending;
    fixture.detectChanges();

    expect(codeField(fixture)).toBeNull();
    expect(component.error()).toBe('');
  });

  it('asks for a code on the email form only after the server demands one', async () => {
    const fixture = await render();
    const http = TestBed.inject(HttpTestingController);
    const component = fixture.componentInstance;
    component.newEmail = 'new@example.com';
    component.emailPassword = 'correct-horse-battery-staple';

    const gated = component.requestEmailChange();
    http
      .expectOne('/api/v1/me/email-change')
      .flush(
        { errorCode: 'AUTH_MFA_STEP_UP_REQUIRED' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await gated;
    fixture.detectChanges();

    expect(codeField(fixture)).not.toBeNull();
    expect(component.error()).toBe('');
    expect(component.emailPassword).toBe('correct-horse-battery-staple');

    component.emailPrompt.code = '123456';
    const retry = component.requestEmailChange();
    const accepted = http.expectOne('/api/v1/me/email-change');
    expect(accepted.request.body).toEqual({
      currentPassword: 'correct-horse-battery-staple',
      newEmail: 'new@example.com',
      code: '123456',
    });
    accepted.flush(null, { status: 204, statusText: 'No Content' });
    await retry;
    fixture.detectChanges();

    expect(codeField(fixture)).toBeNull();
  });

  it('confirms self-deletion once, then only adds the code on the retry', async () => {
    const confirmed = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = await render();
    const http = TestBed.inject(HttpTestingController);
    const component = fixture.componentInstance;
    component.deletionPassword = 'correct-horse-battery-staple';

    const gated = component.deleteAccount();
    http
      .expectOne('/api/v1/me/deletion')
      .flush(
        { errorCode: 'AUTH_MFA_STEP_UP_REQUIRED' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await gated;
    fixture.detectChanges();

    expect(codeField(fixture)).not.toBeNull();
    expect(confirmed).toHaveBeenCalledTimes(1);

    component.deletionPrompt.code = 'AAAA-BBBB-CCCC';
    const retry = component.deleteAccount();
    const accepted = http.expectOne('/api/v1/me/deletion');
    expect(accepted.request.body).toEqual({
      currentPassword: 'correct-horse-battery-staple',
      code: 'AAAA-BBBB-CCCC',
    });
    accepted.flush(null, { status: 204, statusText: 'No Content' });
    await retry;

    expect(confirmed).toHaveBeenCalledTimes(1);
  });
});
