import { Injectable, inject, signal } from '@angular/core';
import { catchError, finalize, map, Observable, of, shareReplay, tap } from 'rxjs';

import { AuthenticationService } from '../shared/generated-api/api/authentication.service';
import type { LoginOutcome } from '../shared/generated-api/model/loginOutcome';
import type { LoginRequest } from '../shared/generated-api/model/loginRequest';
import type { SessionContext } from '../shared/generated-api/model/sessionContext';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly authenticationApi = inject(AuthenticationService);

  readonly session = signal<SessionContext | null>(null);
  readonly restored = signal(false);
  private restoration: Observable<boolean> | null = null;

  restore(): Observable<boolean> {
    return this.authenticationApi.getCurrentSession().pipe(
      tap((session) => this.session.set(session)),
      map(() => true),
      catchError(() => {
        this.clear();
        return of(false);
      }),
      tap(() => this.restored.set(true)),
    );
  }

  ensureRestored(): Observable<boolean> {
    if (this.restored()) return of(this.session() !== null);
    if (!this.restoration) {
      this.restoration = this.restore().pipe(
        finalize(() => {
          this.restoration = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.restoration;
  }

  login(request: LoginRequest): Observable<LoginOutcome> {
    return this.authenticationApi.login(request).pipe(
      tap((outcome) => {
        if (outcome.context) {
          this.session.set(outcome.context);
          this.restored.set(true);
        }
      }),
    );
  }

  logout(): Observable<void> {
    return this.authenticationApi.logout().pipe(
      tap(() => this.clear()),
      map(() => undefined),
    );
  }

  clear(): void {
    this.session.set(null);
    this.restored.set(true);
  }
}
