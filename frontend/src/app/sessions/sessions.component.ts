import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';

import { AuthStore } from '../core/auth.store';
import { SessionsService } from '../shared/generated-api/api/sessions.service';
import type { SessionSummary } from '../shared/generated-api/model/sessionSummary';

@Component({
  selector: 'app-sessions',
  imports: [DatePipe],
  templateUrl: './sessions.component.html',
  styleUrl: './sessions.component.css',
})
export class SessionsComponent {
  private readonly sessionsApi = inject(SessionsService);
  private readonly auth = inject(AuthStore);

  protected readonly sessions = signal<SessionSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected revoke(session: SessionSummary): void {
    this.sessionsApi.revokeSession(session.id).subscribe({
      next: () => {
        if (session.current) {
          this.auth.clear();
          window.location.assign('/login');
          return;
        }
        this.sessions.update((current) => current.filter((item) => item.id !== session.id));
      },
      error: () => this.error.set('The session could not be revoked.'),
    });
  }

  protected revokeOthers(): void {
    this.sessionsApi.revokeOtherSessions().subscribe({
      next: () => this.sessions.update((current) => current.filter((item) => item.current)),
      error: () => this.error.set('Other sessions could not be revoked.'),
    });
  }

  private load(): void {
    this.sessionsApi.listSessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Sessions could not be loaded.');
        this.loading.set(false);
      },
    });
  }
}
