import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n.service';
import { AuthenticationService } from '../shared/generated-api/api/authentication.service';

@Component({
  selector: 'app-verify-email-change',
  imports: [RouterLink],
  template: `
    <main class="lifecycle-shell">
      <section class="lifecycle-card">
        <div class="mark" aria-hidden="true"><i class="fa-solid fa-envelope-circle-check"></i></div>
        <h1>{{ i18n.t('authVerifyEmailChangeTitle') }}</h1>
        @if (busy()) {
          <p aria-busy="true">{{ i18n.t('authVerifyEmailChangeBusy') }}</p>
        } @else if (completed()) {
          <p>{{ i18n.t('authEmailChangedSuccess') }}</p>
          <a routerLink="/login">{{ i18n.t('authContinueToSignIn') }}</a>
        } @else {
          <p role="alert">{{ error() }}</p>
          <a routerLink="/login">{{ i18n.t('authReturnToSignIn') }}</a>
        }
      </section>
    </main>
  `,
  styleUrl: './public-lifecycle.css',
})
export class VerifyEmailChangeComponent {
  private readonly api = inject(AuthenticationService);
  protected readonly i18n = inject(I18nService);
  readonly busy = signal(true);
  readonly completed = signal(false);
  readonly error = signal('');

  constructor(route: ActivatedRoute) {
    const token = route.snapshot.queryParamMap.get('token') ?? '';
    history.replaceState({}, '', '/verify-email-change');
    if (!token) {
      this.busy.set(false);
      this.error.set(this.i18n.t('authVerifyLinkInvalid'));
      return;
    }
    this.api.completeEmailChange({ token }).subscribe({
      next: () => {
        this.busy.set(false);
        this.completed.set(true);
      },
      error: (failure) => {
        this.busy.set(false);
        this.error.set(failure.error?.detail ?? this.i18n.t('authVerifyLinkInvalid'));
      },
    });
  }
}
