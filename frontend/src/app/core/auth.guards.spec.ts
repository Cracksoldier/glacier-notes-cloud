import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { type ActivatedRouteSnapshot, Router, type RouterStateSnapshot } from '@angular/router';
import { firstValueFrom, isObservable, Observable, of, Subject } from 'rxjs';

import { AuthenticatedUserRoleEnum } from '../shared/generated-api/model/authenticatedUser';
import type { SessionContext } from '../shared/generated-api/model/sessionContext';
import { adminGuard, anonymousGuard, authGuard } from './auth.guards';
import { AuthStore } from './auth.store';

describe('authentication route guards', () => {
  const session = signal<SessionContext | null>(null);
  const restored = signal(true);
  const ensureRestored = vi.fn(() => of(false));
  const router = {
    createUrlTree: vi.fn((commands: string[]) => commands),
  };

  beforeEach(() => {
    session.set(null);
    restored.set(true);
    ensureRestored.mockReset();
    ensureRestored.mockReturnValue(of(false));
    router.createUrlTree.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: { session, restored, ensureRestored } },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('redirects anonymous navigation to login', () => {
    expect(run(authGuard)).toEqual(['/login']);
    expect(run(adminGuard)).toEqual(['/login']);
    expect(run(anonymousGuard)).toBe(true);
  });

  it('allows authenticated users and keeps admin routes role-restricted', () => {
    session.set(context(AuthenticatedUserRoleEnum.User));
    expect(run(authGuard)).toBe(true);
    expect(run(anonymousGuard)).toEqual(['/']);
    expect(run(adminGuard)).toEqual(['/']);

    session.set(context(AuthenticatedUserRoleEnum.Admin));
    expect(run(adminGuard)).toBe(true);
  });

  it('waits for initial session restoration before deciding a protected deep link', async () => {
    restored.set(false);
    const restoration = new Subject<boolean>();
    ensureRestored.mockReturnValue(restoration);

    const result = run(authGuard);

    expect(isObservable(result)).toBe(true);
    const decision = firstValueFrom(result as Observable<boolean>);
    let settled = false;
    const observedDecision = decision.then((value) => {
      settled = true;
      return value;
    });
    session.set(context(AuthenticatedUserRoleEnum.User));
    await Promise.resolve();
    expect(settled).toBe(false);
    restoration.next(true);
    restoration.complete();

    await expect(observedDecision).resolves.toBe(true);
    expect(router.createUrlTree).not.toHaveBeenCalled();
  });

  function run(guard: typeof authGuard) {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  function context(role: AuthenticatedUserRoleEnum): SessionContext {
    return {
      user: {
        id: 'bdf602ed-2c9d-4509-9a29-cc7169fb4472',
        username: 'member',
        email: 'member@example.com',
        role,
      },
      session: {
        id: '417946d4-9b47-4582-b72a-5d2a90730899',
        current: true,
        rememberMe: false,
        createdAt: '2026-07-20T19:00:00Z',
        lastSeenAt: '2026-07-20T19:00:00Z',
        expiresAt: '2026-07-21T07:00:00Z',
      },
    };
  }
});
