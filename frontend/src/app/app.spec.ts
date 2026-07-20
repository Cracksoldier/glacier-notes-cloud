import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { App } from './app';
import { provideApi } from './shared/generated-api/provide-api';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    }).compileComponents();
  });

  it('calls the generated API client and renders the connected state', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const statusRequest = TestBed.inject(HttpTestingController).expectOne('/api/v1/setup/status');
    expect(statusRequest.request.method).toBe('GET');
    statusRequest.flush({ setupRequired: false });

    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/ping');
    expect(request.request.method).toBe('GET');
    request.flush({
      service: 'glacier-notes-cloud',
      status: 'ok',
      apiVersion: 'v1',
      serverTime: '2026-07-20T19:00:00Z',
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'API connected',
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
