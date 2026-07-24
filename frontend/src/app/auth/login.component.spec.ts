import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { provideApi } from '../shared/generated-api/provide-api';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApi(''),
      ],
    }).compileComponents();
  });

  it('submits username or email, password, and remember-me through the generated client', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance as unknown as {
      form: {
        setValue(value: { identifier: string; password: string; rememberMe: boolean }): void;
      };
      submit(): void;
    };
    component.form.setValue({
      identifier: 'Admin@Example.com',
      password: 'correct-horse-battery-staple',
      rememberMe: true,
    });

    component.submit();

    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      identifier: 'Admin@Example.com',
      password: 'correct-horse-battery-staple',
      rememberMe: true,
    });
  });

  it('associates required-field feedback with both login fields', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const identifier = fixture.nativeElement.querySelector(
      'input[formControlName="identifier"]',
    ) as HTMLInputElement;
    const password = fixture.nativeElement.querySelector(
      'input[formControlName="password"]',
    ) as HTMLInputElement;
    expect(identifier.getAttribute('aria-describedby')).toBe('identifier-error');
    expect(password.getAttribute('aria-describedby')).toBe('password-error');
    expect(fixture.nativeElement.querySelector('#identifier-error')?.textContent).toContain(
      'required',
    );
    expect(fixture.nativeElement.querySelector('#password-error')?.textContent).toContain(
      'required',
    );
  });
});
