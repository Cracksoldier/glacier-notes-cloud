import { Component, inject, signal } from '@angular/core';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminStatus } from '../shared/generated-api/model/adminStatus';

@Component({
  selector: 'app-admin-status',
  template: `
    <main class="page">
      <section>
        <p class="eyebrow">{{ i18n.t('administration') }}</p>
        <h1>{{ i18n.t('adminStatusTitle') }}</h1>
        @if (status(); as value) {
          <dl>
            <div><dt>{{ i18n.t('adminStatusService') }}</dt><dd>{{ value.service }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusApi') }}</dt><dd>{{ value.apiVersion }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusApplicationVersion') }}</dt><dd>{{ value.applicationVersion }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusBuild') }}</dt><dd>{{ value.buildIdentifier }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusDatabase') }}</dt><dd>{{ value.database }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusImageBackend') }}</dt><dd>{{ value.imageStorageBackend }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusImageStorage') }}</dt><dd>{{ value.imageStorage }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusSmtp') }}</dt><dd>{{ value.smtp.state }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusBackups') }}</dt><dd>{{ value.backupEnabled ? i18n.t('adminStatusEnabled') : i18n.t('adminStatusDisabled') }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusMetrics') }}</dt><dd>{{ value.metricsEnabled ? i18n.t('adminStatusEnabled') : i18n.t('adminStatusDisabled') }}</dd></div>
            <div><dt>{{ i18n.t('adminStatusJobs') }}</dt><dd>{{ value.jobsHealthy ? i18n.t('adminStatusHealthy') : i18n.t('adminStatusDegraded') }}</dd></div>
          </dl>
        } @else if (error()) {
          <p role="alert">{{ error() }}</p>
        } @else {
          <p role="status">{{ i18n.t('adminStatusLoading') }}</p>
        }
      </section>
    </main>
  `,
  styles: `
    .page { min-height: calc(100vh - 4.5rem); padding: clamp(1.25rem, 5vw, 4rem); }
    section { width: min(52rem, 100%); margin: 0 auto; }
    .eyebrow { color: #87c7d8; font-size: .75rem; font-weight: 700; letter-spacing: .13em; text-transform: uppercase; }
    h1 { color: #eef8fb; font-size: clamp(2rem, 6vw, 3.5rem); }
    dl { display: grid; gap: .75rem; }
    dl div { display: flex; justify-content: space-between; padding: 1rem; border: 1px solid #29424e; border-radius: .75rem; background: rgb(7 19 27 / 74%); }
    dt { color: #8fa9b2; } dd { margin: 0; color: #eef8fb; text-transform: uppercase; }
  `,
})
export class AdminStatusComponent {
  private readonly administrationApi = inject(AdministrationService);
  protected readonly i18n = inject(I18nService);
  protected readonly status = signal<AdminStatus | null>(null);
  protected readonly error = signal('');

  constructor() {
    this.administrationApi.getAdminStatus().subscribe({
      next: (status) => this.status.set(status),
      error: (failure) =>
        this.error.set(failure.error?.detail ?? this.i18n.t('adminStatusLoadFailed')),
    });
  }
}
