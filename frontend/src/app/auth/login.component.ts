import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthStore } from '../core/auth.store';
import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

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
          const problem = error.error as ProblemDetails | null;
          if (error.status === 429) {
            this.retryAfter.set(Number(error.headers.get('Retry-After') ?? 1));
          }
          this.errorMessage.set(
            problem?.detail ?? 'Sign in failed. Check your details and try again.',
          );
        } else {
          this.errorMessage.set('Sign in failed. Check your connection and try again.');
        }
      },
    });
  }
}
