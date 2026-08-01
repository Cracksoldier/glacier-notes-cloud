import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';
import { I18nService, type MessageKey } from './i18n.service';

export interface ProblemNotice {
  id: number;
  message: string;
}

const PROBLEM_CODE_KEYS: Record<string, MessageKey> = {
  AUTH_INVALID_CREDENTIALS: 'problemCodeAuthInvalidCredentials',
  AUTH_SESSION_EXPIRED: 'problemCodeAuthSessionExpired',
  AUTH_FORBIDDEN: 'problemCodeAuthForbidden',
  AUTH_RATE_LIMITED: 'problemCodeAuthRateLimited',
  CSRF_INVALID: 'problemCodeCsrfInvalid',
  SETUP_DENIED: 'problemCodeSetupDenied',
  SETUP_UNAVAILABLE: 'problemCodeSetupUnavailable',
  SETUP_CONFLICT: 'problemCodeSetupConflict',
  SETUP_RATE_LIMITED: 'problemCodeSetupRateLimited',
  LIFECYCLE_UNAVAILABLE: 'problemCodeLifecycleUnavailable',
  LIFECYCLE_RATE_LIMITED: 'problemCodeLifecycleRateLimited',
  IDENTITY_CONFLICT: 'problemCodeIdentityConflict',
  ACCOUNT_STATE_CONFLICT: 'problemCodeAccountStateConflict',
  LAST_ADMIN_REQUIRED: 'problemCodeLastAdminRequired',
  CURRENT_PASSWORD_INVALID: 'problemCodeCurrentPasswordInvalid',
  TOKEN_INVALID_OR_EXPIRED: 'problemCodeTokenInvalidOrExpired',
  CONTENT_CONFLICT: 'problemCodeContentConflict',
  CONTENT_STATE_CONFLICT: 'problemCodeContentStateConflict',
  CONTENT_VERSION_CONFLICT: 'problemCodeContentVersionConflict',
  ENTITY_NOT_FOUND: 'problemCodeEntityNotFound',
  VALIDATION_FAILED: 'problemCodeValidationFailed',
  RATE_LIMITED: 'problemCodeRateLimited',
  REQUEST_CONFLICT: 'problemCodeRequestConflict',
  REQUEST_INVALID: 'problemCodeRequestInvalid',
  REQUEST_TOO_LARGE: 'problemCodeRequestTooLarge',
  REQUEST_REJECTED: 'problemCodeRequestRejected',
  UNSUPPORTED_MEDIA_TYPE: 'problemCodeUnsupportedMediaType',
  METHOD_NOT_ALLOWED: 'problemCodeMethodNotAllowed',
  NOT_ACCEPTABLE: 'problemCodeNotAcceptable',
  FEATURE_DISABLED: 'problemCodeFeatureDisabled',
  SMTP_NOT_CONFIGURED: 'problemCodeSmtpNotConfigured',
  INTERNAL_ERROR: 'problemCodeInternalError',
  IMAGE_QUOTA_EXCEEDED: 'problemCodeImageQuotaExceeded',
  IMAGE_STILL_REFERENCED: 'problemCodeImageStillReferenced',
  IMAGE_STORAGE_UNAVAILABLE: 'problemCodeImageStorageUnavailable',
  AUTH_MFA_INVALID_CODE: 'problemCodeAuthMfaInvalidCode',
  AUTH_MFA_CHALLENGE_INVALID: 'problemCodeAuthMfaChallengeInvalid',
  AUTH_MFA_ATTEMPTS_EXCEEDED: 'problemCodeAuthMfaAttemptsExceeded',
  MFA_ALREADY_ENROLLED: 'problemCodeMfaAlreadyEnrolled',
  MFA_NOT_ENROLLED: 'problemCodeMfaNotEnrolled',
  MFA_UNAVAILABLE: 'problemCodeMfaUnavailable',
};

@Injectable({ providedIn: 'root' })
export class ProblemService {
  private readonly i18n = inject(I18nService);
  readonly notices = signal<ProblemNotice[]>([]);

  private nextId = 1;

  message(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return this.i18n.t('problemGeneric');
    }
    if (error.status === 0) {
      return this.i18n.t('problemNetwork');
    }
    const problem = this.problem(error.error);
    const mapped = problem?.errorCode ? PROBLEM_CODE_KEYS[problem.errorCode] : undefined;
    let message: string;
    if (mapped) {
      message = this.i18n.t(mapped);
    } else if (problem?.validationErrors?.length) {
      message = problem.validationErrors.map((item) => item.message).join(' ');
    } else if (error.status === 404) {
      message = this.i18n.t('problemNotFound');
    } else if (error.status === 409) {
      message = problem?.detail || this.i18n.t('problemConflict');
    } else {
      message =
        problem?.detail ||
        problem?.title ||
        this.i18n.t('problemStatusFallback', { status: error.status });
    }
    return problem?.correlationId
      ? `${message} ${this.i18n.t('commonReferenceLabel')}: ${problem.correlationId}`
      : message;
  }

  report(error: unknown): void {
    this.push(this.message(error));
  }

  push(message: string): void {
    const id = this.nextId++;
    this.notices.update((items) => [...items, { id, message }].slice(-4));
    window.setTimeout(() => this.dismiss(id), 6000);
  }

  dismiss(id: number): void {
    this.notices.update((items) => items.filter((item) => item.id !== id));
  }

  isConflict(error: unknown): error is HttpErrorResponse {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  private problem(value: unknown): ProblemDetails | null {
    if (!value || typeof value !== 'object') {
      return null;
    }
    return value as ProblemDetails;
  }
}
