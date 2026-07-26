import { Component, inject, signal } from '@angular/core';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminStatus } from '../shared/generated-api/model/adminStatus';

@Component({
  selector: 'app-admin-status',
  template: `
    <h1>{{ i18n.t('adminStatusTitle') }}</h1>
    @if (status(); as value) {
      <dl>
        <div><dt><i class="fa-solid fa-server" aria-hidden="true"></i> {{ i18n.t('adminStatusService') }}</dt><dd>{{ value.service }}</dd></div>
        <div><dt><i class="fa-solid fa-code-branch" aria-hidden="true"></i> {{ i18n.t('adminStatusApi') }}</dt><dd>{{ value.apiVersion }}</dd></div>
        <div><dt><i class="fa-solid fa-tag" aria-hidden="true"></i> {{ i18n.t('adminStatusApplicationVersion') }}</dt><dd>{{ value.applicationVersion }}</dd></div>
        <div><dt><i class="fa-solid fa-hashtag" aria-hidden="true"></i> {{ i18n.t('adminStatusBuild') }}</dt><dd>{{ value.buildIdentifier }}</dd></div>
        <div><dt><i class="fa-solid fa-database" aria-hidden="true"></i> {{ i18n.t('adminStatusDatabase') }}</dt><dd>{{ value.database }}</dd></div>
        <div><dt><i class="fa-solid fa-hard-drive" aria-hidden="true"></i> {{ i18n.t('adminStatusImageBackend') }}</dt><dd>{{ value.imageStorageBackend }}</dd></div>
        <div><dt><i class="fa-solid fa-image" aria-hidden="true"></i> {{ i18n.t('adminStatusImageStorage') }}</dt><dd>{{ value.imageStorage }}</dd></div>
        <div><dt><i class="fa-solid fa-paper-plane" aria-hidden="true"></i> {{ i18n.t('adminStatusSmtp') }}</dt><dd>{{ value.smtp.state }}</dd></div>
        <div><dt><i class="fa-solid fa-box-archive" aria-hidden="true"></i> {{ i18n.t('adminStatusBackups') }}</dt><dd>{{ value.backupEnabled ? i18n.t('adminStatusEnabled') : i18n.t('adminStatusDisabled') }}</dd></div>
        <div><dt><i class="fa-solid fa-chart-line" aria-hidden="true"></i> {{ i18n.t('adminStatusMetrics') }}</dt><dd>{{ value.metricsEnabled ? i18n.t('adminStatusEnabled') : i18n.t('adminStatusDisabled') }}</dd></div>
        <div><dt><i class="fa-solid fa-gears" aria-hidden="true"></i> {{ i18n.t('adminStatusJobs') }}</dt><dd>{{ value.jobsHealthy ? i18n.t('adminStatusHealthy') : i18n.t('adminStatusDegraded') }}</dd></div>
      </dl>
    } @else if (error()) {
      <p role="alert">{{ error() }}</p>
    } @else {
      <p role="status">{{ i18n.t('adminStatusLoading') }}</p>
    }
  `,
  styleUrl: './admin.css',
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
