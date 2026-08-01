import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import type { MfaStatus } from '../shared/generated-api/model/mfaStatus';
import { MfaStatusStatusEnum } from '../shared/generated-api/model/mfaStatus';
import { provideApi } from '../shared/generated-api/provide-api';
import { TwoFactorCardComponent } from './two-factor-card.component';

describe('TwoFactorCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TwoFactorCardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    }).compileComponents();
  });

  async function render(status: MfaStatus) {
    const fixture = TestBed.createComponent(TwoFactorCardComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/me/mfa').flush(status);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('renders nothing at all when the instance does not offer the feature', async () => {
    const fixture = await render({ status: MfaStatusStatusEnum.None, available: false });

    expect(fixture.nativeElement.querySelector('section')).toBeNull();
  });

  it('offers setup when the feature is available but unused', async () => {
    const fixture = await render({ status: MfaStatusStatusEnum.None, available: true });

    expect(fixture.nativeElement.textContent).toContain('Not set up');
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Set up');
  });

  it('reports the remaining recovery codes while the factor is active', async () => {
    const fixture = await render({
      status: MfaStatusStatusEnum.Active,
      available: true,
      confirmedAt: '2026-07-31T09:00:00Z',
      recoveryCodesRemaining: 7,
    });

    expect(fixture.nativeElement.textContent).toContain('Active');
    expect(fixture.nativeElement.textContent).toContain('7 recovery codes left');
  });

  it('refuses to resume an abandoned setup, because the key cannot be shown twice', async () => {
    const fixture = await render({ status: MfaStatusStatusEnum.Pending, available: true });

    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    expect(buttons).toHaveLength(1);
    expect(buttons[0].textContent).toContain('Discard and start over');
  });

  it('keeps the recovery codes on screen until they are acknowledged', async () => {
    const fixture = await render({ status: MfaStatusStatusEnum.None, available: true });
    const http = TestBed.inject(HttpTestingController);
    const component = fixture.componentInstance as unknown as {
      beginPasswordStep(action: 'start' | 'disable' | 'regenerate'): void;
      submitPassword(): Promise<void>;
      password: string;
      code: string;
      confirmEnrollment(): Promise<void>;
    };

    component.beginPasswordStep('start');
    component.password = 'correct-horse-battery-staple';
    const started = component.submitPassword();
    http.expectOne('/api/v1/me/mfa/totp').flush({
      secret: 'ABCD EFGH IJKL MNOP',
      provisioningUri: 'otpauth://totp/Glacier:member?secret=ABCDEFGHIJKLMNOP',
      expiresAt: new Date(Date.now() + 600_000).toISOString(),
      digits: 6,
      periodSeconds: 30,
    });
    await started;
    fixture.detectChanges();

    component.code = '123456';
    const confirmed = component.confirmEnrollment();
    http.expectOne('/api/v1/me/mfa/totp/confirm').flush({
      codes: ['AAAA-BBBB-CCCC', 'DDDD-EEEE-FFFF'],
      generatedAt: '2026-07-31T09:00:00Z',
    });
    await confirmed;
    http.expectOne('/api/v1/me/mfa').flush({
      status: MfaStatusStatusEnum.Active,
      available: true,
      confirmedAt: '2026-07-31T09:00:00Z',
      recoveryCodesRemaining: 2,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AAAA-BBBB-CCCC');
    const done = [...fixture.nativeElement.querySelectorAll('button')].find((button) =>
      (button as HTMLButtonElement).textContent?.includes('Done'),
    ) as HTMLButtonElement;
    expect(done.disabled).toBe(true);
  });
});
