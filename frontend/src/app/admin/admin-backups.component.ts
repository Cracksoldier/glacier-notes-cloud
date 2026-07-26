import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { BackupJob } from '../shared/generated-api/model/backupJob';

@Component({
  selector: 'app-admin-backups',
  template: `
    <h1>{{ i18n.t('adminBackupsTitle') }}</h1>
    <p role="note">{{ i18n.t('adminBackupsIntro') }}</p>
    <div class="actions">
      <button type="button" [disabled]="creating()" (click)="create()">
        <i class="fa-solid fa-plus" aria-hidden="true"></i>
        <span>{{ creating() ? i18n.t('adminBackupsQueuing') : i18n.t('adminBackupsCreate') }}</span>
      </button>
    </div>
    @if (error()) { <p role="alert">{{ error() }}</p> }
    <div class="list">
      @for (job of jobs(); track job.id) {
        <article class="card">
          <strong><i class="fa-solid fa-database" aria-hidden="true"></i>{{ job.state }}</strong>
          <span>{{ job.createdAt }}</span>
          <span>{{ i18n.t('adminBackupsInitiatedBy', { user: job.createdByUserId }) }}</span>
          @if (job.outputIdentifier) { <span>{{ i18n.t('adminBackupsServerIdentifier', { id: job.outputIdentifier }) }}</span> }
          @if (job.byteSize !== undefined) { <span>{{ i18n.t('adminBackupsBytesChecksum', { bytes: job.byteSize, checksum: job.checksum ?? '' }) }}</span> }
          @if (job.errorMessage) { <span>{{ job.errorMessage }}</span> }
        </article>
      }
    </div>
  `,
  styleUrl: './admin.css',
})
export class AdminBackupsComponent {
  private readonly api = inject(AdministrationService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly i18n = inject(I18nService);
  readonly jobs = signal<BackupJob[]>([]);
  readonly creating = signal(false);
  readonly error = signal('');

  constructor() {
    timer(0, 3000)
      .pipe(
        switchMap(() => this.api.listBackups(undefined, 50)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (page) => this.jobs.set(page.items),
        error: (failure) =>
          this.error.set(failure.error?.detail ?? this.i18n.t('adminBackupsLoadFailed')),
      });
  }

  create(): void {
    if (this.creating()) return;
    this.creating.set(true);
    this.error.set('');
    this.api.createBackup().subscribe({
      next: (job) => {
        this.jobs.update((jobs) => [job, ...jobs.filter((value) => value.id !== job.id)]);
        this.creating.set(false);
      },
      error: (failure) => {
        this.error.set(failure.error?.detail ?? this.i18n.t('adminBackupsQueueFailed'));
        this.creating.set(false);
      },
    });
  }
}
