import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom, timer } from 'rxjs';

import { I18nService } from '../core/i18n.service';
import { ProblemService } from '../core/problem.service';
import { StepUpPrompt } from '../core/step-up';
import { CurrentUserService } from '../shared/generated-api/api/currentUser.service';
import type { MfaEnrollmentStart } from '../shared/generated-api/model/mfaEnrollmentStart';
import { type MfaStatus, MfaStatusStatusEnum } from '../shared/generated-api/model/mfaStatus';
import { StepUpCodeComponent } from '../shared/step-up-code.component';

type Step = 'idle' | 'password' | 'scan' | 'codes';
type PasswordAction = 'start' | 'disable' | 'regenerate';

@Component({
  selector: 'app-two-factor-card',
  imports: [FormsModule, StepUpCodeComponent],
  templateUrl: './two-factor-card.component.html',
  styleUrl: './two-factor-card.component.css',
})
export class TwoFactorCardComponent {
  private readonly api = inject(CurrentUserService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly problems = inject(ProblemService);
  protected readonly i18n = inject(I18nService);
  protected readonly statusEnum = MfaStatusStatusEnum;

  protected readonly status = signal<MfaStatus | null>(null);
  protected readonly step = signal<Step>('idle');
  protected readonly enrollment = signal<MfaEnrollmentStart | null>(null);
  protected readonly qrPath = signal<{ d: string; size: number } | null>(null);
  protected readonly recoveryCodes = signal<string[]>([]);
  protected readonly acknowledged = signal(false);
  protected readonly copied = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly minutesRemaining = signal(0);
  protected readonly prompt = new StepUpPrompt();

  private passwordAction: PasswordAction = 'start';

  password = '';
  code = '';

  constructor() {
    void this.loadStatus();
    timer(0, 15_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshExpiry());
  }

  protected beginPasswordStep(action: PasswordAction): void {
    if (action === 'disable' && !window.confirm(this.i18n.t('mfaCardTurnOffConfirm'))) return;
    if (action === 'regenerate' && !window.confirm(this.i18n.t('mfaCardRegenerateConfirm'))) return;
    this.passwordAction = action;
    this.password = '';
    this.prompt.clear();
    this.error.set('');
    this.step.set('password');
  }

  protected async submitPassword(): Promise<void> {
    await this.run(async () => {
      const currentPassword = this.password;
      // Starting an enrollment is password-only: no factor is active yet to step up with.
      if (this.passwordAction === 'start') {
        await this.startEnrollment(currentPassword);
      } else if (this.passwordAction === 'disable') {
        await firstValueFrom(this.api.disableTotp({ currentPassword, code: this.prompt.value() }));
        this.reset();
      } else {
        const codes = await firstValueFrom(
          this.api.regenerateRecoveryCodes({ currentPassword, code: this.prompt.value() }),
        );
        this.showCodes(codes.codes);
      }
      this.password = '';
    });
  }

  protected async confirmEnrollment(): Promise<void> {
    await this.run(async () => {
      const codes = await firstValueFrom(
        this.api.confirmTotpEnrollment({ code: this.code.trim() }),
      );
      this.code = '';
      this.showCodes(codes.codes);
    });
  }

  protected async discardPending(): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.api.cancelTotpEnrollment());
      this.reset();
    });
  }

  protected async cancel(): Promise<void> {
    if (this.step() === 'scan') {
      await this.run(() => firstValueFrom(this.api.cancelTotpEnrollment()).then(() => undefined));
    }
    this.reset();
  }

  protected async copyCodes(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.recoveryCodes().join('\n'));
      this.copied.set(true);
    } catch {
      this.error.set(this.i18n.t('mfaCardCopyFailed'));
    }
  }

  protected downloadCodes(): void {
    const url = URL.createObjectURL(
      new Blob([`${this.recoveryCodes().join('\n')}\n`], { type: 'text/plain' }),
    );
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'glacier-recovery-codes.txt';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  protected dismissCodes(): void {
    this.recoveryCodes.set([]);
    this.reset();
  }

  private async startEnrollment(currentPassword: string): Promise<void> {
    const enrollment = await firstValueFrom(this.api.startTotpEnrollment({ currentPassword }));
    this.enrollment.set(enrollment);
    this.qrPath.set(await this.renderQr(enrollment.provisioningUri));
    this.code = '';
    this.refreshExpiry();
    this.step.set('scan');
  }

  /** Kept out of the initial bundle: the QR library is only needed while enrolling. */
  private async renderQr(uri: string): Promise<{ d: string; size: number }> {
    const { encode } = await import('uqr');
    const result = encode(uri, { border: 2 });
    const segments: string[] = [];
    result.data.forEach((row, y) => {
      row.forEach((dark, x) => {
        if (dark) segments.push(`M${x} ${y}h1v1h-1z`);
      });
    });
    return { d: segments.join(''), size: result.size };
  }

  private showCodes(codes: string[]): void {
    this.recoveryCodes.set(codes);
    this.acknowledged.set(false);
    this.copied.set(false);
    this.enrollment.set(null);
    this.qrPath.set(null);
    this.step.set('codes');
    void this.loadStatus();
  }

  private reset(): void {
    this.step.set('idle');
    this.enrollment.set(null);
    this.qrPath.set(null);
    this.password = '';
    this.code = '';
    this.prompt.clear();
    void this.loadStatus();
  }

  private refreshExpiry(): void {
    const expiresAt = this.enrollment()?.expiresAt;
    if (!expiresAt) return;
    const minutes = Math.max(0, Math.ceil((Date.parse(expiresAt) - Date.now()) / 60_000));
    this.minutesRemaining.set(minutes);
    if (minutes === 0) {
      this.error.set(this.i18n.t('mfaCardSetupExpired'));
      this.reset();
    }
  }

  private async loadStatus(): Promise<void> {
    try {
      this.status.set(await firstValueFrom(this.api.getMfaStatus()));
    } catch {
      this.status.set(null);
      this.error.set(this.i18n.t('mfaCardLoadFailed'));
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await action();
    } catch (failure) {
      if (!this.prompt.handle(failure)) this.error.set(this.problems.message(failure));
    } finally {
      this.busy.set(false);
    }
  }
}
