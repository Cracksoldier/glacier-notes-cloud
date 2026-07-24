import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthenticationService } from '../shared/generated-api/api/authentication.service';

@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: './public-lifecycle.css',
})
export class ResetPasswordComponent {
  private readonly api = inject(AuthenticationService);
  readonly done = signal(false);
  readonly error = signal('');
  readonly busy = signal(false);
  token = '';
  password = '';

  constructor(route: ActivatedRoute) {
    this.token = route.snapshot.queryParamMap.get('token') ?? '';
    if (this.token) history.replaceState({}, '', '/reset-password');
  }

  submit(): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.api.completePasswordReset({ token: this.token, password: this.password }).subscribe({
      next: () => {
        this.password = '';
        this.busy.set(false);
        this.done.set(true);
      },
      error: (failure) => {
        this.password = '';
        this.busy.set(false);
        this.error.set(failure.error?.detail ?? 'The reset link is invalid or expired.');
      },
    });
  }
}
