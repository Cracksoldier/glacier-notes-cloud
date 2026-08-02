import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthenticatedUserRoleEnum } from '../shared/generated-api/model/authenticatedUser';
import {
  type LoginOutcome,
  LoginOutcomeResultEnum,
} from '../shared/generated-api/model/loginOutcome';
import type { LoginRequest } from '../shared/generated-api/model/loginRequest';
import { MfaChallengeAcceptedFactorsEnum } from '../shared/generated-api/model/mfaChallenge';
import type { SessionContext } from '../shared/generated-api/model/sessionContext';
import { provideApi } from '../shared/generated-api/provide-api';
import { AuthStore } from './auth.store';

describe('AuthStore', () => {
  const request: LoginRequest = {
    identifier: 'member',
    password: 'correct-horse-battery-staple',
    rememberMe: false,
  };
  const sessionContext: SessionContext = {
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
      context: sessionContext,
    };

    store.login(request).subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login').flush(outcome);

    expect(store.session()).toEqual(outcome.context);
    expect(store.restored()).toBe(true);
    expect(store.challenge()).toBeNull();
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

  it('holds the challenge from an MFA_REQUIRED outcome and trades it for a session', () => {
    const store = TestBed.inject(AuthStore);
    const http = TestBed.inject(HttpTestingController);

    store.login(request).subscribe();
    http.expectOne('/api/v1/auth/login').flush({
      result: LoginOutcomeResultEnum.MfaRequired,
      challenge: {
        token: 'challenge-token',
        expiresAt: '2026-07-31T09:05:00Z',
        attemptsRemaining: 5,
        acceptedFactors: [MfaChallengeAcceptedFactorsEnum.Totp],
      },
    } satisfies LoginOutcome);

    expect(store.challenge()?.token).toBe('challenge-token');
    expect(store.session()).toBeNull();

    store.completeSecondFactor('123456').subscribe();
    const completion = http.expectOne('/api/v1/auth/login/mfa');
    expect(completion.request.body).toEqual({ challengeToken: 'challenge-token', code: '123456' });
    completion.flush(sessionContext);

    expect(store.session()).toEqual(sessionContext);
    expect(store.challenge()).toBeNull();
  });

  it('drops the challenge when it is abandoned or the store is cleared', () => {
    const store = TestBed.inject(AuthStore);

    store.login(request).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/login')
      .flush({
        result: LoginOutcomeResultEnum.MfaRequired,
        challenge: {
          token: 'challenge-token',
          expiresAt: '2026-07-31T09:05:00Z',
          attemptsRemaining: 5,
          acceptedFactors: [MfaChallengeAcceptedFactorsEnum.Totp],
        },
      } satisfies LoginOutcome);

    store.abandonChallenge();
    expect(store.challenge()).toBeNull();
  });

  it('reports a missing challenge through the observable rather than synchronously', () => {
    const store = TestBed.inject(AuthStore);
    let failure: unknown;

    store.completeSecondFactor('123456').subscribe({ error: (error) => (failure = error) });

    expect(failure).toBeInstanceOf(Error);
    TestBed.inject(HttpTestingController).expectNone('/api/v1/auth/login/mfa');
  });
});
