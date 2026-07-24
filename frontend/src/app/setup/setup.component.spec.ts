import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideApi } from '../shared/generated-api/provide-api';
import { SetupComponent } from './setup.component';

describe('SetupComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SetupComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    }).compileComponents();
  });

  it('sends the token as a header and excludes confirmation from the body', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    const component = fixture.componentInstance;
    const initialized = vi.fn();
    component.initialized.subscribe(initialized);

    component.form.setValue({
      username: 'admin',
      email: 'admin@example.com',
      displayName: 'Administrator',
      password: 'correct-horse-battery-staple',
      passwordConfirmation: 'correct-horse-battery-staple',
      bootstrapToken: 'bootstrap-secret-value',
    });
    component.submit();

    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/setup/administrator');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-Bootstrap-Token')).toBe('bootstrap-secret-value');
    expect(request.request.body).toEqual({
      username: 'admin',
      email: 'admin@example.com',
      displayName: 'Administrator',
      password: 'correct-horse-battery-staple',
      language: 'en',
    });
    request.flush({ initialized: true, initializedAt: '2026-07-20T19:00:00Z' });

    expect(initialized).toHaveBeenCalledOnce();
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.bootstrapToken.value).toBe('');
  });

  it('does not send mismatched passwords', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    const component = fixture.componentInstance;
    component.form.setValue({
      username: 'admin',
      email: 'admin@example.com',
      displayName: '',
      password: 'correct-horse-battery-staple',
      passwordConfirmation: 'different-password-value',
      bootstrapToken: 'bootstrap-secret-value',
    });

    component.submit();

    TestBed.inject(HttpTestingController).expectNone('/api/v1/setup/administrator');
    expect(component.fieldErrors()).toEqual({ passwordConfirmation: 'Passwords do not match' });
  });

  it('renders server validation errors and associates them with their controls', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    const component = fixture.componentInstance;
    component.form.setValue({
      username: 'admin',
      email: 'admin@example.com',
      displayName: '',
      password: 'correct-horse-battery-staple',
      passwordConfirmation: 'correct-horse-battery-staple',
      bootstrapToken: 'bootstrap-secret-value',
    });

    component.submit();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/setup/administrator')
      .flush(
        {
          detail: 'Validation failed.',
          validationErrors: [{ field: 'username', message: 'Username is already reserved.' }],
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    const username = fixture.nativeElement.querySelector('#username') as HTMLInputElement;
    expect(username.getAttribute('aria-describedby')).toContain('username-error');
    expect(fixture.nativeElement.querySelector('#username-error')?.textContent).toContain(
      'already reserved',
    );
  });

  it('renders retry timing after setup throttling', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    const component = fixture.componentInstance;
    component.form.setValue({
      username: 'admin',
      email: 'admin@example.com',
      displayName: '',
      password: 'correct-horse-battery-staple',
      passwordConfirmation: 'correct-horse-battery-staple',
      bootstrapToken: 'bootstrap-secret-value',
    });

    component.submit();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/setup/administrator')
      .flush(
        { detail: 'Try later.' },
        {
          status: 429,
          statusText: 'Too Many Requests',
          headers: new HttpHeaders({ 'Retry-After': '17' }),
        },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      '17 seconds',
    );
  });

  it('uses a valid inherited font family on the setup action', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();

    const styles = Array.from(document.querySelectorAll('style'))
      .map((style) => style.textContent)
      .join('\n');
    expect(styles).toContain('font-weight: 700');
    expect(styles).not.toContain('font: 700 0.9rem / 1 inherit');
  });
});
