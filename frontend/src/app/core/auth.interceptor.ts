import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';
import { AuthStore } from './auth.store';

/** These answer 401 for a wrong password or code, which is not an expired session. */
const LOGIN_PATHS = ['/api/v1/auth/login', '/api/v1/auth/login/mfa'];

/**
 * The step-up gate also answers 401, on a session that is very much alive — it wants proof of
 * possession before one particular operation. Signing the caller out on these would make every
 * gated operation impossible to complete.
 */
const STEP_UP_CODES = new Set([
  'AUTH_MFA_STEP_UP_REQUIRED',
  'AUTH_STEP_UP_PASSWORD_REQUIRED',
  'AUTH_MFA_INVALID_CODE',
]);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !LOGIN_PATHS.some((path) => request.url.endsWith(path)) &&
        !STEP_UP_CODES.has(errorCode(error))
      ) {
        auth.clear();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};

function errorCode(error: HttpErrorResponse): string {
  const problem = error.error as ProblemDetails | null;
  return (problem && typeof problem === 'object' && problem.errorCode) || '';
}
