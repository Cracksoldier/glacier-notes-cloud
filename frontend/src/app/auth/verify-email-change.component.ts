import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthenticationService } from '../shared/generated-api/api/authentication.service';

@Component({
  selector: 'app-verify-email-change',
  imports: [RouterLink],
  template: `
    <main class="lifecycle-shell">
      <section class="lifecycle-card">
        <div class="mark" aria-hidden="true"><i class="fa-solid fa-envelope-circle-check"></i></div>
        <h1>Verify email change</h1>
        @if (busy()) {
          <p aria-busy="true">Verifying your new email address…</p>
        } @else if (completed()) {
          <p>Your email address was changed. Sign in again with the new address.</p>
          <a routerLink="/login">Continue to sign in</a>
        } @else {
          <p role="alert">{{ error() }}</p>
          <a routerLink="/login">Return to sign in</a>
        }
      </section>
    </main>
  `,
  styleUrl: './public-lifecycle.css',
})
export class VerifyEmailChangeComponent {
  private readonly api = inject(AuthenticationService);
  readonly busy = signal(true);
  readonly completed = signal(false);
  readonly error = signal('');

  constructor(route: ActivatedRoute) {
    const token = route.snapshot.queryParamMap.get('token') ?? '';
    history.replaceState({}, '', '/verify-email-change');
    if (!token) {
      this.busy.set(false);
      this.error.set('This verification link is invalid or expired.');
      return;
    }
    this.api.completeEmailChange({ token }).subscribe({
      next: () => {
        this.busy.set(false);
        this.completed.set(true);
      },
      error: (failure) => {
        this.busy.set(false);
        this.error.set(failure.error?.detail ?? 'This verification link is invalid or expired.');
      },
    });
  }
}
