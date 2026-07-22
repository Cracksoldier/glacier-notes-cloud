import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, inject, Output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { SetupService } from '../shared/generated-api/api/setup.service';
import { InitialAdministratorRequestLanguageEnum } from '../shared/generated-api/model/initialAdministratorRequest';
import type { ProblemDetails } from '../shared/generated-api/model/problemDetails';

interface ControlErrors {
  required?: boolean;
  email?: boolean;
  minlength?: { requiredLength: number };
  maxlength?: { requiredLength: number };
  pattern?: unknown;
}

@Component({
  selector: 'app-setup',
  imports: [ReactiveFormsModule],
  templateUrl: './setup.component.html',
  styleUrl: './setup.component.css',
})
export class SetupComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly setupApi = inject(SetupService);

  @Output() readonly initialized = new EventEmitter<void>();

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});

  readonly form = this.formBuilder.nonNullable.group({
    username: [
      '',
      [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(64),
        Validators.pattern(/^[\p{L}\p{N}._-]+$/u),
      ],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    displayName: ['', [Validators.maxLength(128)]],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(128),
        Validators.pattern(/^\S+$/),
      ],
    ],
    passwordConfirmation: ['', Validators.required],
    bootstrapToken: ['', Validators.required],
  });

  submit(): void {
    this.errorMessage.set(null);
    this.retryAfter.set(null);
    this.fieldErrors.set({});
    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const values = this.form.getRawValue();
    if (values.password !== values.passwordConfirmation) {
      this.fieldErrors.set({ passwordConfirmation: 'Passwords do not match' });
      return;
    }

    this.submitting.set(true);
    this.setupApi
      .createInitialAdministrator(values.bootstrapToken, {
        username: values.username,
        email: values.email,
        displayName: values.displayName || undefined,
        password: values.password,
        language: navigator.language.toLowerCase().startsWith('de')
          ? InitialAdministratorRequestLanguageEnum.De
          : InitialAdministratorRequestLanguageEnum.En,
      })
      .subscribe({
        next: () => {
          this.clearSecrets();
          this.submitting.set(false);
          this.initialized.emit();
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.handleError(error);
        },
      });
  }

  protected errorFor(field: string): string | null {
    const apiError = this.fieldErrors()[field];
    if (apiError) {
      return apiError;
    }
    const control = this.form.get(field);
    if (!control?.touched || !control.errors) {
      return null;
    }
    const errors = control.errors as ControlErrors;
    if (errors.required) {
      return 'This field is required';
    }
    if (errors.email) {
      return 'Enter a valid email address';
    }
    if (errors.minlength) {
      return `Use at least ${errors.minlength.requiredLength} characters`;
    }
    if (errors.maxlength) {
      return `Use no more than ${errors.maxlength.requiredLength} characters`;
    }
    if (errors.pattern) {
      return field === 'password'
        ? 'Password must not contain whitespace'
        : 'Use letters, numbers, dots, underscores, or hyphens';
    }
    return 'Check this value';
  }

  private handleError(error: HttpErrorResponse): void {
    const problem = this.problemDetails(error.error);
    if (problem?.validationErrors) {
      this.fieldErrors.set(
        Object.fromEntries(problem.validationErrors.map((entry) => [entry.field, entry.message])),
      );
    }
    if (error.status === 429) {
      const seconds = Number.parseInt(error.headers.get('Retry-After') ?? '', 10);
      this.retryAfter.set(Number.isFinite(seconds) ? seconds : null);
    }
    this.errorMessage.set(
      problem?.detail ?? 'Setup could not be completed. Check the server and try again.',
    );
  }

  private problemDetails(value: unknown): ProblemDetails | null {
    if (typeof value !== 'object' || value === null || !('detail' in value)) {
      return null;
    }
    return value as ProblemDetails;
  }

  private clearSecrets(): void {
    this.form.controls.password.reset('');
    this.form.controls.passwordConfirmation.reset('');
    this.form.controls.bootstrapToken.reset('');
  }
}
