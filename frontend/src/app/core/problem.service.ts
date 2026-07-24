import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';

import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';

export interface ProblemNotice {
  id: number;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ProblemService {
  readonly notices = signal<ProblemNotice[]>([]);

  private nextId = 1;

  message(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Something went wrong. Try again.';
    }
    if (error.status === 0) {
      return 'The server could not be reached. Your changes are still in this browser.';
    }
    const problem = this.problem(error.error);
    let message: string;
    if (error.status === 404) {
      message = 'This item is no longer available.';
    } else if (error.status === 409) {
      message = problem?.detail || 'This item changed in another session.';
    } else if (problem?.validationErrors?.length) {
      message = problem.validationErrors.map((item) => item.message).join(' ');
    } else {
      message = problem?.detail || problem?.title || `The request failed (${error.status}).`;
    }
    return problem?.correlationId ? `${message} Reference: ${problem.correlationId}` : message;
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
