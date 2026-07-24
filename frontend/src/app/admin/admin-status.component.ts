import { Component, inject, signal } from '@angular/core';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminStatus } from '../shared/generated-api/model/adminStatus';

@Component({
  selector: 'app-admin-status',
  template: `
    <main class="page">
      <section>
        <p class="eyebrow">Administration</p>
        <h1>Instance status</h1>
        @if (status(); as value) {
          <dl>
            <div><dt>Service</dt><dd>{{ value.service }}</dd></div>
            <div><dt>API</dt><dd>{{ value.apiVersion }}</dd></div>
            <div><dt>Application version</dt><dd>{{ value.applicationVersion }}</dd></div>
            <div><dt>Build</dt><dd>{{ value.buildIdentifier }}</dd></div>
            <div><dt>Database</dt><dd>{{ value.database }}</dd></div>
            <div><dt>Image backend</dt><dd>{{ value.imageStorageBackend }}</dd></div>
            <div><dt>Image storage</dt><dd>{{ value.imageStorage }}</dd></div>
            <div><dt>SMTP</dt><dd>{{ value.smtp.state }}</dd></div>
            <div><dt>Backups</dt><dd>{{ value.backupEnabled ? 'enabled' : 'disabled' }}</dd></div>
            <div><dt>Metrics</dt><dd>{{ value.metricsEnabled ? 'enabled' : 'disabled' }}</dd></div>
            <div><dt>Scheduled jobs</dt><dd>{{ value.jobsHealthy ? 'healthy' : 'degraded' }}</dd></div>
          </dl>
        } @else if (error()) {
          <p role="alert">{{ error() }}</p>
        } @else {
          <p role="status">Loading administrative status…</p>
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
  protected readonly status = signal<AdminStatus | null>(null);
  protected readonly error = signal('');

  constructor() {
    this.administrationApi.getAdminStatus().subscribe({
      next: (status) => this.status.set(status),
      error: (failure) =>
        this.error.set(
          failure.error?.detail ?? 'Administrative status could not be loaded. Try again.',
        ),
    });
  }
}
