import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminStatus } from '../shared/generated-api/model/adminStatus';
import { AuditEventResultEnum } from '../shared/generated-api/model/auditEvent';
import { SmtpStatusStateEnum } from '../shared/generated-api/model/smtpStatus';
import { AdminAuditComponent } from './admin-audit.component';
import { AdminSmtpComponent } from './admin-smtp.component';

describe('M11 administration operations', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('renders normalized audit information returned by the filtered API', () => {
    const listAuditEvents = vi.fn().mockReturnValue(
      of({
        items: [
          {
            id: '75b3949c-ee64-43f0-aa41-3a579786d7d6',
            eventType: 'INSTANCE_SETTINGS_CHANGED',
            occurredAt: '2026-07-24T12:00:00Z',
            result: AuditEventResultEnum.Success,
            ipAddress: '127.0.0.1',
            clientDescription: 'Chrome / Linux',
            correlationId: 'settings-request',
            metadata: {},
          },
        ],
        page: { size: 1, hasNext: false },
      }),
    );
    TestBed.configureTestingModule({
      providers: [{ provide: AdministrationService, useValue: { listAuditEvents } }],
    });

    const fixture = TestBed.createComponent(AdminAuditComponent);
    fixture.detectChanges();

    expect(listAuditEvents).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('INSTANCE_SETTINGS_CHANGED');
    expect(fixture.nativeElement.textContent).toContain('Chrome / Linux');
    expect(fixture.nativeElement.textContent).toContain('settings-request');
  });

  it('runs an SMTP test without exposing credential fields', () => {
    const smtp = {
      configured: true,
      senderName: 'Glacier Notes',
      senderAddress: 'notes@example.test',
      state: SmtpStatusStateEnum.Ready,
    };
    const testSmtp = vi.fn().mockReturnValue(of({ ...smtp, state: SmtpStatusStateEnum.Succeeded }));
    const status = { smtp } as AdminStatus;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AdministrationService,
          useValue: { getAdminStatus: () => of(status), testSmtp },
        },
      ],
    });

    const fixture = TestBed.createComponent(AdminSmtpComponent);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(testSmtp).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Test email sent.');
    expect(fixture.nativeElement.querySelector('input[type="password"]')).toBeNull();
  });
});
