import { Component, inject, signal } from '@angular/core';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { SmtpStatus } from '../shared/generated-api/model/smtpStatus';

@Component({
  selector: 'app-admin-smtp',
  template: `
    <h1>SMTP status</h1>
    <p>SMTP credentials remain deployment secrets and are never returned to this dashboard.</p>
    @if (status(); as value) {
      <dl>
        <div><dt>Configured</dt><dd>{{ value.configured ? 'Yes' : 'No' }}</dd></div>
        <div><dt>Sender</dt><dd>{{ value.senderName }} &lt;{{ value.senderAddress }}&gt;</dd></div>
        <div><dt>State</dt><dd>{{ value.state }}</dd></div>
        <div><dt>Last success</dt><dd>{{ value.lastSuccessfulAt ?? 'Never' }}</dd></div>
        <div><dt>Last failure</dt><dd>{{ value.lastFailureCategory ?? 'None' }}</dd></div>
      </dl>
      <button type="button" [disabled]="!value.configured || testing()" (click)="test()">
        {{ testing() ? 'Sending…' : 'Send test email to me' }}
      </button>
    } @else {
      <p role="status">Loading SMTP status…</p>
    }
    @if (message()) { <p role="status">{{ message() }}</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
  `,
  styleUrl: './admin.css',
})
export class AdminSmtpComponent {
  private readonly api = inject(AdministrationService);
  readonly status = signal<SmtpStatus | null>(null);
  readonly testing = signal(false);
  readonly message = signal('');
  readonly error = signal('');

  constructor() {
    this.api.getAdminStatus().subscribe({
      next: (value) => this.status.set(value.smtp),
      error: (failure) =>
        this.error.set(failure.error?.detail ?? 'SMTP status could not be loaded.'),
    });
  }

  test(): void {
    if (this.testing()) return;
    this.testing.set(true);
    this.message.set('');
    this.error.set('');
    this.api.testSmtp().subscribe({
      next: (value) => {
        this.status.set(value);
        this.message.set(value.state === 'SUCCEEDED' ? 'Test email sent.' : 'SMTP test failed.');
        this.testing.set(false);
      },
      error: (failure) => {
        this.error.set(failure.error?.detail ?? 'SMTP test could not be completed.');
        this.testing.set(false);
      },
    });
  }
}
