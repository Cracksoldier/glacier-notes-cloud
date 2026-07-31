import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthenticatedUserRoleEnum } from '../shared/generated-api/model/authenticatedUser';
import {
  type LoginOutcome,
  LoginOutcomeResultEnum,
} from '../shared/generated-api/model/loginOutcome';
import type { LoginRequest } from '../shared/generated-api/model/loginRequest';
import { provideApi } from '../shared/generated-api/provide-api';
import { AuthStore } from './auth.store';

describe('AuthStore', () => {
  const request: LoginRequest = {
    identifier: 'member',
    password: 'correct-horse-battery-staple',
    rememberMe: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    });
  });

  it('adopts the session carried by a SESSION login outcome', () => {
    const store = TestBed.inject(AuthStore);
    const outcome: LoginOutcome = {
      result: LoginOutcomeResultEnum.Session,
      context: {
        user: {
          id: 'f8d0d8b6-4a0c-4d0e-8c0a-5f2a1c3b9e11',
          username: 'member',
          email: 'member@example.com',
          role: AuthenticatedUserRoleEnum.User,
        },
        session: {
          id: '9c4a1f9c-1a1e-4f27-9a3f-2f1a5f0f4d22',
          current: true,
          rememberMe: false,
          createdAt: '2026-07-31T09:00:00Z',
          lastSeenAt: '2026-07-31T09:00:00Z',
          expiresAt: '2026-07-31T21:00:00Z',
        },
      },
    };

    store.login(request).subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login').flush(outcome);

    expect(store.session()).toEqual(outcome.context);
    expect(store.restored()).toBe(true);
  });

  it('leaves the session unset when the outcome carries no session', () => {
    const store = TestBed.inject(AuthStore);

    store.login(request).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/login')
      .flush({ result: LoginOutcomeResultEnum.MfaRequired } satisfies LoginOutcome);

    expect(store.session()).toBeNull();
    expect(store.restored()).toBe(false);
  });
});
