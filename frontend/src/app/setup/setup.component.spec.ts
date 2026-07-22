import { provideHttpClient } from '@angular/common/http';
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
});
