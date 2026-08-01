import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { MfaChallengeAcceptedFactorsEnum } from '../shared/generated-api/model/mfaChallenge';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';

describe('authInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  });

  function reject(url: string): ReturnType<typeof vi.spyOn> {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    TestBed.inject(HttpClient)
      .get(url)
      .subscribe({ error: () => undefined });
    TestBed.inject(HttpTestingController)
      .expectOne(url)
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    return navigate;
  }

  it('treats a rejected request as an expired session', () => {
    const store = TestBed.inject(AuthStore);
    store.restored.set(true);

    const navigate = reject('/api/v1/notes');

    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(store.session()).toBeNull();
  });

  it('leaves a second-factor challenge intact when the code is merely wrong', () => {
    const store = TestBed.inject(AuthStore);
    store.challenge.set({
      token: 'challenge-token',
      expiresAt: '2026-07-31T09:05:00Z',
      attemptsRemaining: 5,
      acceptedFactors: [MfaChallengeAcceptedFactorsEnum.Totp],
    });

    const navigate = reject('/api/v1/auth/login/mfa');

    expect(navigate).not.toHaveBeenCalled();
    expect(store.challenge()?.token).toBe('challenge-token');
  });
});
