import { Injectable, inject, signal } from '@angular/core';
import { catchError, map, Observable, of, tap } from 'rxjs';

import { AuthenticationService } from '../shared/generated-api/api/authentication.service';
import type { LoginRequest } from '../shared/generated-api/model/loginRequest';
import type { SessionContext } from '../shared/generated-api/model/sessionContext';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly authenticationApi = inject(AuthenticationService);

  readonly session = signal<SessionContext | null>(null);

  restore(): Observable<boolean> {
    return this.authenticationApi.getCurrentSession().pipe(
      tap((session) => this.session.set(session)),
      map(() => true),
      catchError(() => {
        this.clear();
        return of(false);
      }),
    );
  }

  login(request: LoginRequest): Observable<SessionContext> {
    return this.authenticationApi.login(request).pipe(tap((session) => this.session.set(session)));
  }

  logout(): Observable<void> {
    return this.authenticationApi.logout().pipe(
      tap(() => this.clear()),
      map(() => undefined),
    );
  }

  clear(): void {
    this.session.set(null);
  }
}
