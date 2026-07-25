import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthStore } from '../core/auth.store';
import { I18nService } from '../core/i18n.service';
import { ProblemService } from '../core/problem.service';
import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';

interface LoginControlErrors {
  required?: boolean;
  maxlength?: { requiredLength: number };
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly problems = inject(ProblemService);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);
  protected readonly form = new FormGroup({
    identifier: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(320)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(128)],
    }),
    rememberMe: new FormControl(false, { nonNullable: true }),
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.retryAfter.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.form.controls.password.setValue('');
        if (error instanceof HttpErrorResponse) {
          if (error.status === 429) {
            this.retryAfter.set(Number(error.headers.get('Retry-After') ?? 1));
          }
          if (error.status === 0) {
            this.errorMessage.set(this.i18n.t('authSignInNetworkFailure'));
            return;
          }
          const problem = error.error as ProblemDetails | null;
          if (problem?.errorCode || problem?.title) {
            this.errorMessage.set(this.problems.message(error));
          } else {
            this.errorMessage.set(this.i18n.t('authSignInFailed'));
          }
        } else {
          this.errorMessage.set(this.i18n.t('authSignInNetworkFailure'));
        }
      },
    });
  }

  protected errorFor(field: 'identifier' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
    const errors = control.errors as LoginControlErrors;
    if (errors.required) return this.i18n.t('commonRequiredField');
    if (errors.maxlength) {
      return this.i18n.t('commonMaxLength', { length: errors.maxlength.requiredLength });
    }
    return this.i18n.t('commonCheckValue');
  }
}
