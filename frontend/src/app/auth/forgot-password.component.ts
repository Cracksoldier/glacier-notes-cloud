import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthenticationService } from '../shared/generated-api/api/authentication.service';

@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './public-lifecycle.css',
})
export class ForgotPasswordComponent {
  private readonly api = inject(AuthenticationService);
  readonly sent = signal(false);
  email = '';

  submit(): void {
    this.api.requestPasswordReset({ email: this.email }).subscribe({
      next: () => this.sent.set(true),
      error: () => this.sent.set(true),
    });
  }
}
