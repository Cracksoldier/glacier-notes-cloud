import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AuditEvent } from '../shared/generated-api/model/auditEvent';

@Component({
  selector: 'app-admin-audit',
  imports: [FormsModule],
  template: `
    <h1>{{ i18n.t('adminAuditTitle') }}</h1>
    <p>{{ i18n.t('adminAuditIntro') }}</p>
    <form class="filters" (ngSubmit)="load()">
      <label>{{ i18n.t('adminAuditEventType') }}<input name="eventType" maxlength="64" [(ngModel)]="eventType"></label>
      <label>{{ i18n.t('adminAuditResult') }}<select name="result" [(ngModel)]="result">
        <option value="">{{ i18n.t('adminAuditAny') }}</option><option value="SUCCESS">{{ i18n.t('adminAuditSuccess') }}</option>
        <option value="FAILURE">{{ i18n.t('adminAuditFailure') }}</option><option value="DENIED">{{ i18n.t('adminAuditDenied') }}</option>
      </select></label>
      <button type="submit">{{ i18n.t('adminAuditApplyFilters') }}</button>
    </form>
    <div class="actions">
      <button type="button" (click)="export('csv')">{{ i18n.t('adminAuditExportCsv') }}</button>
      <button type="button" (click)="export('json')">{{ i18n.t('adminAuditExportJson') }}</button>
    </div>
    @if (loading()) { <p role="status">{{ i18n.t('adminAuditLoading') }}</p> }
    @if (error()) { <p role="alert">{{ error() }}</p> }
    <div class="table-wrap">
      <table>
        <thead><tr><th>{{ i18n.t('adminAuditColumnTime') }}</th><th>{{ i18n.t('adminAuditColumnEvent') }}</th><th>{{ i18n.t('adminAuditColumnResult') }}</th><th>{{ i18n.t('adminAuditColumnClient') }}</th><th>{{ i18n.t('adminAuditColumnCorrelationId') }}</th></tr></thead>
        <tbody>
          @for (event of events(); track event.id) {
            <tr>
              <td>{{ event.occurredAt }}</td><td>{{ event.eventType }}</td><td>{{ event.result }}</td>
              <td>{{ event.ipAddress ?? '—' }} · {{ event.clientDescription ?? i18n.t('adminAuditBackground') }}</td>
              <td><code>{{ event.correlationId }}</code></td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (nextCursor()) {
      <button type="button" (click)="loadMore()">{{ i18n.t('adminAuditLoadMore') }}</button>
    }
  `,
  styleUrl: './admin.css',
})
export class AdminAuditComponent {
  private readonly api = inject(AdministrationService);
  protected readonly i18n = inject(I18nService);
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
          this.error.set(failure.error?.detail ?? this.i18n.t('adminAuditLoadFailed'));
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
          this.error.set(failure.error?.detail ?? this.i18n.t('adminAuditExportFailed')),
      });
  }
}
