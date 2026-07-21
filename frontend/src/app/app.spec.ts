import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { provideApi } from './shared/generated-api/provide-api';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideApi(''),
      ],
    }).compileComponents();
  });

  it('checks setup and restores the current session', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const statusRequest = TestBed.inject(HttpTestingController).expectOne('/api/v1/setup/status');
    expect(statusRequest.request.method).toBe('GET');
    statusRequest.flush({ setupRequired: false });

    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/session');
    request.flush({
      user: {
        id: 'bdf602ed-2c9d-4509-9a29-cc7169fb4472',
        username: 'admin',
        email: 'admin@example.com',
        role: 'ADMIN',
      },
      session: {
        id: '417946d4-9b47-4582-b72a-5d2a90730899',
        current: true,
        rememberMe: false,
        createdAt: '2026-07-20T19:00:00Z',
        lastSeenAt: '2026-07-20T19:00:00Z',
        expiresAt: '2026-07-21T07:00:00Z',
      },
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.wordmark')?.textContent).toContain(
      'Glacier Notes',
    );
  });

  it('renders first-run setup when the instance is uninitialized', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/setup/status')
      .flush({ setupRequired: true });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-setup')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain(
      'Create your administrator',
    );
  });
});
