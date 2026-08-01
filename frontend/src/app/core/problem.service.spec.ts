import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { I18nService } from './i18n.service';
import { ProblemService } from './problem.service';

describe('ProblemService', () => {
  let service: ProblemService;
  let i18n: I18nService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ProblemService);
    i18n = TestBed.inject(I18nService);
    i18n.set('en');
  });

  function httpError(
    status: number,
    body: Record<string, unknown> | null = null,
  ): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: body });
  }

  it('returns a localized generic message for non-HTTP errors', () => {
    expect(service.message(new Error('boom'))).toBe('Something went wrong. Try again.');
    i18n.set('de');
    expect(service.message(new Error('boom'))).toBe(
      'Etwas ist schiefgelaufen. Bitte erneut versuchen.',
    );
  });

  it('reports a network error for status 0', () => {
    expect(service.message(httpError(0))).toContain('server could not be reached');
    i18n.set('de');
    expect(service.message(httpError(0))).toContain('Server ist nicht erreichbar');
  });

  it('maps known errorCode values through the dictionary', () => {
    const error = httpError(401, { errorCode: 'AUTH_INVALID_CREDENTIALS' });
    expect(service.message(error)).toBe('Sign in failed. Check your username and password.');
    i18n.set('de');
    expect(service.message(error)).toBe(
      'Anmeldung fehlgeschlagen. Bitte Benutzername und Passwort prüfen.',
    );
  });

  it('translates the step-up codes rather than echoing English server prose', () => {
    const stepUp = httpError(401, {
      errorCode: 'AUTH_MFA_STEP_UP_REQUIRED',
      detail: 'Step-up authentication is required.',
    });
    const password = httpError(401, { errorCode: 'AUTH_STEP_UP_PASSWORD_REQUIRED' });
    expect(service.message(stepUp)).toBe('This change needs a one-time code.');
    expect(service.message(password)).toBe('Confirm your password to complete this change.');
    i18n.set('de');
    expect(service.message(stepUp)).toBe('Diese Änderung erfordert einen Einmalcode.');
    expect(service.message(password)).toBe(
      'Bestätigen Sie Ihr Passwort, um diese Änderung abzuschließen.',
    );
  });

  it('joins validation error messages when no known code is present', () => {
    const error = httpError(422, {
      validationErrors: [
        { field: 'email', message: 'Enter a valid address.' },
        { field: 'password', message: 'Too short.' },
      ],
    });
    expect(service.message(error)).toBe('Enter a valid address. Too short.');
  });

  it('falls back to translated status messages for 404 and 409', () => {
    expect(service.message(httpError(404))).toBe('This item is no longer available.');
    expect(service.message(httpError(409))).toBe('This item changed in another session.');
  });

  it('substitutes the status code into the fallback template', () => {
    expect(service.message(httpError(500))).toBe('The request failed (500).');
  });

  it('prefers backend detail over the status fallback', () => {
    const error = httpError(500, { detail: 'Custom backend text.' });
    expect(service.message(error)).toBe('Custom backend text.');
  });

  it('appends a localized reference label when a correlation id is present', () => {
    const error = httpError(500, {
      errorCode: 'INTERNAL_ERROR',
      correlationId: 'abc-123',
    });
    expect(service.message(error)).toBe(
      'The server encountered an internal error. Reference: abc-123',
    );
    i18n.set('de');
    expect(service.message(error)).toBe(
      'Auf dem Server ist ein interner Fehler aufgetreten. Referenz: abc-123',
    );
  });
});
