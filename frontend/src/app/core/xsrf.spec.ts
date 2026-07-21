import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SessionsService } from '../shared/generated-api/api/sessions.service';
import { provideApi } from '../shared/generated-api/provide-api';

describe('XSRF configuration', () => {
  beforeEach(() => {
    // biome-ignore lint/suspicious/noDocumentCookie: Angular's XSRF integration reads document.cookie.
    document.cookie = 'GLACIER_CSRF=csrf-test-value; Path=/';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'GLACIER_CSRF', headerName: 'X-CSRF-Token' }),
        ),
        provideHttpClientTesting(),
        provideApi(''),
      ],
    });
  });

  it('sends the readable CSRF cookie on state-changing generated-client requests', () => {
    TestBed.inject(SessionsService).revokeOtherSessions().subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne('/api/v1/me/sessions');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('X-CSRF-Token')).toBe('csrf-test-value');
    request.flush(null);
  });
});
