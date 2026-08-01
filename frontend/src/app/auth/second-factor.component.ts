import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  type ElementRef,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { timer } from 'rxjs';

import { AuthStore } from '../core/auth.store';
import { I18nService } from '../core/i18n.service';
import { ProblemService } from '../core/problem.service';
import { MfaChallengeAcceptedFactorsEnum } from '../shared/generated-api/model/mfaChallenge';
import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';

/** Codes that end the challenge: the server has discarded it, so the client must go back a stage. */
const TERMINAL_CODES = new Set(['AUTH_MFA_CHALLENGE_INVALID', 'AUTH_MFA_ATTEMPTS_EXCEEDED']);

@Component({
  selector: 'app-second-factor',
  imports: [FormsModule],
  templateUrl: './second-factor.component.html',
  styleUrl: './login.component.css',
})
export class SecondFactorComponent {
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly i18n = inject(I18nService);
  private readonly problems = inject(ProblemService);

  readonly verified = output<void>();
  readonly abandoned = output<string | null>();

  protected readonly code = signal('');
  protected readonly submitting = signal(false);
  protected readonly usingRecoveryCode = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);
  protected readonly attemptsRemaining = signal(this.auth.challenge()?.attemptsRemaining ?? 0);
  protected readonly secondsRemaining = signal(0);

  protected readonly recoveryCodesAccepted = computed(() =>
    (this.auth.challenge()?.acceptedFactors ?? []).includes(
      MfaChallengeAcceptedFactorsEnum.RecoveryCode,
    ),
  );

  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');

  constructor() {
    afterNextRender(() => this.codeInput()?.nativeElement.focus());
    const expiresAt = Date.parse(this.auth.challenge()?.expiresAt ?? '');
    timer(0, 1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        const seconds = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
        this.secondsRemaining.set(seconds);
        if (seconds === 0) this.abandon(this.i18n.t('mfaLoginExpired'));
      });
  }

  protected submit(): void {
    const code = this.code().trim();
    if (!code || this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.retryAfter.set(null);
    this.auth.completeSecondFactor(code).subscribe({
      next: () => {
        this.submitting.set(false);
        this.verified.emit();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.code.set('');
        this.handleFailure(error);
      },
    });
  }

  protected toggleRecoveryCode(): void {
    this.usingRecoveryCode.update((using) => !using);
    this.code.set('');
    this.errorMessage.set(null);
  }

  protected abandon(message: string | null = null): void {
    this.auth.abandonChallenge();
    this.abandoned.emit(message);
  }

  private handleFailure(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      this.errorMessage.set(this.i18n.t('problemGeneric'));
      return;
    }
    if (error.status === 429) {
      this.retryAfter.set(Number(error.headers.get('Retry-After') ?? 1));
    }
    const message = this.problems.message(error);
    const errorCode = (error.error as ProblemDetails | null)?.errorCode;
    if (errorCode && TERMINAL_CODES.has(errorCode)) {
      this.abandon(message);
      return;
    }
    this.errorMessage.set(message);
    this.attemptsRemaining.update((remaining) => Math.max(0, remaining - 1));
  }
}
