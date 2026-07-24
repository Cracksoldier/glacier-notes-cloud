import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { BackupJob } from '../shared/generated-api/model/backupJob';

@Component({
  selector: 'app-admin-backups',
  template: `
    <h1>Full server backups</h1>
    <p role="note">
      Backups remain on the configured server volume and contain sensitive user data and password
      hashes. Protect and encrypt that volume externally.
    </p>
    <button type="button" [disabled]="creating()" (click)="create()">
      {{ creating() ? 'Queuing…' : 'Create backup' }}
    </button>
    @if (error()) { <p role="alert">{{ error() }}</p> }
    <div class="list">
      @for (job of jobs(); track job.id) {
        <article class="card">
          <strong>{{ job.state }}</strong>
          <span>{{ job.createdAt }}</span>
          <span>Initiated by {{ job.createdByUserId }}</span>
          @if (job.outputIdentifier) { <span>Server identifier: {{ job.outputIdentifier }}</span> }
          @if (job.byteSize !== undefined) { <span>{{ job.byteSize }} bytes · SHA-256 {{ job.checksum }}</span> }
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
          this.error.set(failure.error?.detail ?? 'Backup jobs could not be loaded.'),
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
        this.error.set(failure.error?.detail ?? 'The backup could not be queued.');
        this.creating.set(false);
      },
    });
  }
}
