import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthStore } from '../core/auth.store';
import {
  type MfaChallenge,
  MfaChallengeAcceptedFactorsEnum,
} from '../shared/generated-api/model/mfaChallenge';
import { provideApi } from '../shared/generated-api/provide-api';
import { SecondFactorComponent } from './second-factor.component';

describe('SecondFactorComponent', () => {
  const challenge = (
    acceptedFactors = [
      MfaChallengeAcceptedFactorsEnum.Totp,
      MfaChallengeAcceptedFactorsEnum.RecoveryCode,
    ],
  ): MfaChallenge => ({
    token: 'challenge-token',
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
    attemptsRemaining: 5,
    acceptedFactors,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SecondFactorComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    }).compileComponents();
  });

  async function render(value: MfaChallenge = challenge()) {
    TestBed.inject(AuthStore).challenge.set(value);
    const fixture = TestBed.createComponent(SecondFactorComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  async function submit(fixture: Awaited<ReturnType<typeof render>>, code: string): Promise<void> {
    const input = fixture.nativeElement.querySelector('#mfa-code') as HTMLInputElement;
    input.value = code;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
  }

  it('sends the typed code with the challenge token and reports success', async () => {
    const fixture = await render();
    const verified = vi.fn();
    fixture.componentInstance.verified.subscribe(verified);

    await submit(fixture, '123456');

    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login/mfa');
    expect(request.request.body).toEqual({ challengeToken: 'challenge-token', code: '123456' });
    request.flush({ user: {}, session: {} });

    expect(verified).toHaveBeenCalled();
  });

  it('clears the field and keeps the stage after a merely wrong code', async () => {
    const fixture = await render();
    const abandoned = vi.fn();
    fixture.componentInstance.abandoned.subscribe(abandoned);

    await submit(fixture, '000000');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/login/mfa')
      .flush({ errorCode: 'AUTH_MFA_INVALID_CODE' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(abandoned).not.toHaveBeenCalled();
    expect((fixture.nativeElement.querySelector('#mfa-code') as HTMLInputElement).value).toBe('');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'not correct',
    );
  });

  it('hands control back to the password stage once the server discards the challenge', async () => {
    const fixture = await render();
    const abandoned = vi.fn();
    fixture.componentInstance.abandoned.subscribe(abandoned);

    await submit(fixture, '000000');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/login/mfa')
      .flush(
        { errorCode: 'AUTH_MFA_ATTEMPTS_EXCEEDED' },
        { status: 429, statusText: 'Too Many Requests' },
      );

    expect(abandoned).toHaveBeenCalledWith(expect.stringContaining('Too many incorrect codes'));
    expect(TestBed.inject(AuthStore).challenge()).toBeNull();
  });

  it('offers the recovery-code path only when the challenge accepts one', async () => {
    const withRecovery = await render();
    expect(withRecovery.nativeElement.textContent).toContain('recovery code');

    withRecovery.destroy();

    const totpOnly = await render(challenge([MfaChallengeAcceptedFactorsEnum.Totp]));
    expect(totpOnly.nativeElement.textContent).not.toContain('recovery code');
  });
});
