import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { provideApi } from '../shared/generated-api/provide-api';
import { AcceptInvitationComponent } from './accept-invitation.component';
import { ForgotPasswordComponent } from './forgot-password.component';
import { ResetPasswordComponent } from './reset-password.component';

describe('public lifecycle flows', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AcceptInvitationComponent, ForgotPasswordComponent, ResetPasswordComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApi(''),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({ token: 'full-secret-token' }) },
          },
        },
      ],
    }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  it('inspects a URL invitation, removes the token from history, and accepts it', () => {
    const replaceState = vi.spyOn(history, 'replaceState');
    const fixture = TestBed.createComponent(AcceptInvitationComponent);
    const component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);

    expect(replaceState).toHaveBeenCalledWith({}, '', '/accept-invitation');
    const inspection = http.expectOne('/api/v1/auth/invitations/inspect');
    expect(inspection.request.body).toEqual({ token: 'full-secret-token' });
    inspection.flush({
      emailHint: 'm***@example.com',
      proposedUsername: 'member',
      displayName: 'Member',
      expiresAt: '2026-07-28T16:00:00Z',
    });

    component.password = 'correct-horse-battery-staple';
    component.accept();
    const acceptance = http.expectOne('/api/v1/auth/invitations/accept');
    expect(acceptance.request.body).toEqual({
      token: 'full-secret-token',
      username: 'member',
      displayName: 'Member',
      password: 'correct-horse-battery-staple',
      language: 'en',
    });
    acceptance.flush(null);

    expect(component.completed()).toBe(true);
    expect(component.password).toBe('');
  });

  it('shows the same neutral result when a reset request fails', () => {
    const component = TestBed.createComponent(ForgotPasswordComponent).componentInstance;
    component.email = 'unknown@example.com';
    component.submit();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/password-reset/request')
      .flush({ detail: 'Unavailable' }, { status: 503, statusText: 'Unavailable' });

    expect(component.sent()).toBe(true);
  });

  it('completes a URL password reset without retaining the password', () => {
    const replaceState = vi.spyOn(history, 'replaceState');
    const component = TestBed.createComponent(ResetPasswordComponent).componentInstance;
    expect(replaceState).toHaveBeenCalledWith({}, '', '/reset-password');

    component.password = 'new-correct-horse-battery-staple';
    component.submit();
    const request = TestBed.inject(HttpTestingController).expectOne(
      '/api/v1/auth/password-reset/complete',
    );
    expect(request.request.body).toEqual({
      token: 'full-secret-token',
      password: 'new-correct-horse-battery-staple',
    });
    request.flush(null);

    expect(component.done()).toBe(true);
    expect(component.password).toBe('');
  });

  it('masks manually entered invitation and reset bearer tokens', () => {
    const invitation = TestBed.createComponent(AcceptInvitationComponent);
    invitation.detectChanges();
    expect(
      (invitation.nativeElement.querySelector('input[name="token"]') as HTMLInputElement).type,
    ).toBe('password');
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/auth/invitations/inspect')
      .flush({ detail: 'Invalid' }, { status: 404, statusText: 'Not Found' });

    const reset = TestBed.createComponent(ResetPasswordComponent);
    reset.detectChanges();
    expect(
      (reset.nativeElement.querySelector('input[name="token"]') as HTMLInputElement).type,
    ).toBe('password');
  });

  it('keeps a password-reset token single-flight while completion is pending', () => {
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    const component = fixture.componentInstance;
    component.password = 'new-correct-horse-battery-staple';
    fixture.detectChanges();

    component.submit();
    component.submit();
    fixture.detectChanges();

    const requests = TestBed.inject(HttpTestingController).match(
      '/api/v1/auth/password-reset/complete',
    );
    expect(requests).toHaveLength(1);
    expect(
      (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled,
    ).toBe(true);
    requests.forEach((request) => {
      request.flush(null);
    });
  });
});
