import { TestBed } from '@angular/core/testing';
import { throwError } from 'rxjs';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import { AdminStatusComponent } from './admin-status.component';

describe('AdminStatusComponent', () => {
  it('replaces the loading state with a visible error when status loading fails', () => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AdministrationService,
          useValue: {
            getAdminStatus: () =>
              throwError(() => ({ error: { detail: 'Administrative status is unavailable.' } })),
          },
        },
      ],
    });

    const fixture = TestBed.createComponent(AdminStatusComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Administrative status is unavailable.',
    );
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });
});
