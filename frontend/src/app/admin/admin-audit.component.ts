import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AuditEvent } from '../shared/generated-api/model/auditEvent';

@Component({
  selector: 'app-admin-audit',
  imports: [FormsModule],
  template: `
    <h1>Audit log</h1>
    <p>Audit records are append-only and are removed only by the configured retention job.</p>
    <form class="filters" (ngSubmit)="load()">
      <label>Event type<input name="eventType" maxlength="64" [(ngModel)]="eventType"></label>
      <label>Result<select name="result" [(ngModel)]="result">
        <option value="">Any</option><option value="SUCCESS">Success</option>
        <option value="FAILURE">Failure</option><option value="DENIED">Denied</option>
      </select></label>
      <button type="submit">Apply filters</button>
    </form>
    <div class="actions">
      <button type="button" (click)="export('csv')">Export CSV</button>
      <button type="button" (click)="export('json')">Export JSON</button>
    </div>
    @if (loading()) { <p role="status">Loading audit events…</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
    <div class="table-wrap">
      <table>
        <thead><tr><th>Time</th><th>Event</th><th>Result</th><th>Client</th><th>Correlation ID</th></tr></thead>
        <tbody>
          @for (event of events(); track event.id) {
            <tr>
              <td>{{ event.occurredAt }}</td><td>{{ event.eventType }}</td><td>{{ event.result }}</td>
              <td>{{ event.ipAddress ?? '—' }} · {{ event.clientDescription ?? 'Background' }}</td>
              <td><code>{{ event.correlationId }}</code></td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (nextCursor()) {
      <button type="button" (click)="loadMore()">Load more</button>
    }
  `,
  styleUrl: './admin.css',
})
export class AdminAuditComponent {
  private readonly api = inject(AdministrationService);
  readonly events = signal<AuditEvent[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly nextCursor = signal<string | undefined>(undefined);
  eventType = '';
  result: '' | 'SUCCESS' | 'FAILURE' | 'DENIED' = '';

  constructor() {
    this.load();
  }

  load(cursor?: string): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .listAuditEvents(
        this.eventType || undefined,
        this.result || undefined,
        undefined,
        undefined,
        cursor,
        50,
      )
      .subscribe({
        next: (page) => {
          this.events.set(cursor ? [...this.events(), ...page.items] : page.items);
          this.nextCursor.set(page.page.nextCursor);
          this.loading.set(false);
        },
        error: (failure) => {
          this.error.set(failure.error?.detail ?? 'Audit events could not be loaded.');
          this.loading.set(false);
        },
      });
  }

  loadMore(): void {
    const cursor = this.nextCursor();
    if (cursor) this.load(cursor);
  }

  export(format: 'csv' | 'json'): void {
    this.api
      .exportAuditEvents(format, this.eventType || undefined, this.result || undefined)
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `glacier-audit.${format}`;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (failure) =>
          this.error.set(failure.error?.detail ?? 'Audit export could not be created.'),
      });
  }
}
