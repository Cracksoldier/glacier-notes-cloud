import { Component, inject, signal } from '@angular/core';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { SmtpStatus } from '../shared/generated-api/model/smtpStatus';

@Component({
  selector: 'app-admin-smtp',
  template: `
    <h1>{{ i18n.t('adminSmtpTitle') }}</h1>
    <p>{{ i18n.t('adminSmtpIntro') }}</p>
    @if (status(); as value) {
      <dl>
        <div><dt>{{ i18n.t('adminSmtpConfigured') }}</dt><dd>{{ value.configured ? i18n.t('adminSmtpYes') : i18n.t('adminSmtpNo') }}</dd></div>
        <div><dt>{{ i18n.t('adminSmtpSender') }}</dt><dd>{{ value.senderName }} &lt;{{ value.senderAddress }}&gt;</dd></div>
        <div><dt>{{ i18n.t('adminSmtpState') }}</dt><dd>{{ value.state }}</dd></div>
        <div><dt>{{ i18n.t('adminSmtpLastSuccess') }}</dt><dd>{{ value.lastSuccessfulAt ?? i18n.t('adminSmtpNever') }}</dd></div>
        <div><dt>{{ i18n.t('adminSmtpLastFailure') }}</dt><dd>{{ value.lastFailureCategory ?? i18n.t('adminSmtpNone') }}</dd></div>
      </dl>
      <div class="actions">
        <button type="button" [disabled]="!value.configured || testing()" (click)="test()">
          <i class="fa-solid fa-vial" aria-hidden="true"></i>
          <span>{{ testing() ? i18n.t('adminSmtpSending') : i18n.t('adminSmtpSendTestEmail') }}</span>
        </button>
      </div>
    } @else {
      <p role="status">{{ i18n.t('adminSmtpLoading') }}</p>
    }
    @if (message()) { <p role="status">{{ message() }}</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
  `,
  styleUrl: './admin.css',
})
export class AdminSmtpComponent {
  private readonly api = inject(AdministrationService);
  protected readonly i18n = inject(I18nService);
  readonly status = signal<SmtpStatus | null>(null);
  readonly testing = signal(false);
  readonly message = signal('');
  readonly error = signal('');

  constructor() {
    this.api.getAdminStatus().subscribe({
      next: (value) => this.status.set(value.smtp),
      error: (failure) =>
        this.error.set(failure.error?.detail ?? this.i18n.t('adminSmtpStatusLoadFailed')),
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
        this.message.set(
          value.state === 'SUCCEEDED'
            ? this.i18n.t('adminSmtpTestSent')
            : this.i18n.t('adminSmtpTestFailed'),
        );
        this.testing.set(false);
      },
      error: (failure) => {
        this.error.set(failure.error?.detail ?? this.i18n.t('adminSmtpTestUnavailable'));
        this.testing.set(false);
      },
    });
  }
}
