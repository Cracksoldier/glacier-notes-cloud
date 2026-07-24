import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminStatus } from '../shared/generated-api/model/adminStatus';

@Component({
  selector: 'app-admin-overview',
  imports: [RouterLink],
  template: `
    <h1>Administration overview</h1>
    <p>Operational health and safe administration for this Glacier Notes instance.</p>
    @if (status(); as value) {
      <div class="status-grid">
        <a class="card" routerLink="../status"><strong>Application</strong><span>{{ value.status }} · {{ value.applicationVersion }}</span></a>
        <a class="card" routerLink="../smtp"><strong>SMTP</strong><span>{{ value.smtp.state }}</span></a>
        <a class="card" routerLink="../audit"><strong>Audit</strong><span>Review security and administrative events</span></a>
        @if (value.backupEnabled) {
          <a class="card" routerLink="../backups"><strong>Backups</strong><span>Enabled</span></a>
        } @else {
          <div class="card"><strong>Backups</strong><span>Disabled by deployment</span></div>
        }
        <div class="card"><strong>Metrics</strong><span>{{ value.metricsEnabled ? 'Enabled on management port' : 'Disabled' }}</span></div>
        <div class="card"><strong>Scheduled jobs</strong><span>{{ value.jobsHealthy ? 'Healthy' : 'Degraded' }}</span></div>
      </div>
    } @else if (error()) {
      <p role="alert">{{ error() }}</p>
    } @else {
      <p role="status">Loading administration overview…</p>
    }
  `,
  styleUrl: './admin.css',
})
export class AdminOverviewComponent {
  private readonly api = inject(AdministrationService);
  readonly status = signal<AdminStatus | null>(null);
  readonly error = signal('');

  constructor() {
    this.api.getAdminStatus().subscribe({
      next: (value) => this.status.set(value),
      error: (failure) =>
        this.error.set(failure.error?.detail ?? 'Administration overview could not be loaded.'),
    });
  }
}
