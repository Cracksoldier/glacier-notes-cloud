import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminStatus } from '../shared/generated-api/model/adminStatus';

@Component({
  selector: 'app-admin-overview',
  imports: [RouterLink],
  template: `
    <p class="eyebrow">{{ i18n.t('administration') }}</p>
    <h1>{{ i18n.t('adminOverviewTitle') }}</h1>
    <p>{{ i18n.t('adminOverviewIntro') }}</p>
    @if (status(); as value) {
      <div class="status-grid">
        <a class="card" routerLink="../status"><strong><i class="fa-solid fa-server" aria-hidden="true"></i>{{ i18n.t('adminOverviewApplication') }}</strong><span>{{ value.status }} · {{ value.applicationVersion }}</span></a>
        <a class="card" routerLink="../smtp"><strong><i class="fa-solid fa-paper-plane" aria-hidden="true"></i>{{ i18n.t('adminOverviewSmtp') }}</strong><span>{{ value.smtp.state }}</span></a>
        <a class="card" routerLink="../audit"><strong><i class="fa-solid fa-clipboard-list" aria-hidden="true"></i>{{ i18n.t('adminOverviewAudit') }}</strong><span>{{ i18n.t('adminOverviewAuditIntro') }}</span></a>
        @if (value.backupEnabled) {
          <a class="card" routerLink="../backups"><strong><i class="fa-solid fa-database" aria-hidden="true"></i>{{ i18n.t('adminOverviewBackups') }}</strong><span>{{ i18n.t('adminOverviewBackupsEnabled') }}</span></a>
        } @else {
          <div class="card"><strong><i class="fa-solid fa-database" aria-hidden="true"></i>{{ i18n.t('adminOverviewBackups') }}</strong><span>{{ i18n.t('adminOverviewBackupsDisabled') }}</span></div>
        }
        <div class="card"><strong><i class="fa-solid fa-chart-line" aria-hidden="true"></i>{{ i18n.t('adminOverviewMetrics') }}</strong><span>{{ value.metricsEnabled ? i18n.t('adminOverviewMetricsEnabled') : i18n.t('adminOverviewMetricsDisabled') }}</span></div>
        <div class="card"><strong><i class="fa-solid fa-gears" aria-hidden="true"></i>{{ i18n.t('adminOverviewJobs') }}</strong><span>{{ value.jobsHealthy ? i18n.t('adminOverviewJobsHealthy') : i18n.t('adminOverviewJobsDegraded') }}</span></div>
      </div>
    } @else if (error()) {
      <p role="alert">{{ error() }}</p>
    } @else {
      <p role="status">{{ i18n.t('adminOverviewLoading') }}</p>
    }
  `,
  styleUrl: './admin.css',
})
export class AdminOverviewComponent {
  private readonly api = inject(AdministrationService);
  protected readonly i18n = inject(I18nService);
  readonly status = signal<AdminStatus | null>(null);
  readonly error = signal('');

  constructor() {
    this.api.getAdminStatus().subscribe({
      next: (value) => this.status.set(value),
      error: (failure) =>
        this.error.set(failure.error?.detail ?? this.i18n.t('adminOverviewLoadFailed')),
    });
  }
}
