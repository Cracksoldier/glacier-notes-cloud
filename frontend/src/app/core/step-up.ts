import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';

import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';

const STEP_UP_REQUIRED = 'AUTH_MFA_STEP_UP_REQUIRED';
const INVALID_CODE = 'AUTH_MFA_INVALID_CODE';

/**
 * The grace window is deliberately not on the wire, so the client cannot know whether an operation
 * needs a code until the server says so. One instance per gated form.
 */
export class StepUpPrompt {
  readonly open = signal(false);
  code = '';

  /** True when the server merely asked for a code, and the caller should not report a failure. */
  handle(failure: unknown): boolean {
    const errorCode = this.errorCode(failure);
    if (errorCode === STEP_UP_REQUIRED) {
      this.code = '';
      this.open.set(true);
      return true;
    }
    if (errorCode === INVALID_CODE) {
      this.code = '';
    }
    return false;
  }

  value(): string | undefined {
    return this.open() ? this.code.trim() || undefined : undefined;
  }

  clear(): void {
    this.code = '';
    this.open.set(false);
  }

  private errorCode(failure: unknown): string | undefined {
    if (!(failure instanceof HttpErrorResponse)) return undefined;
    const problem = failure.error as ProblemDetails | null;
    return problem && typeof problem === 'object' ? problem.errorCode : undefined;
  }
}
