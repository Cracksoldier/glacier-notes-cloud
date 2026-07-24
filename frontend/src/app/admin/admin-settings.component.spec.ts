import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import {
  type AdminSettings,
  AdminSettingsAllowedImageTypesEnum,
} from '../shared/generated-api/model/adminSettings';
import { AdminSettingsComponent } from './admin-settings.component';

const settings: AdminSettings = {
  allowedEmailDomains: [],
  invitationExpirationHours: 168,
  passwordResetExpirationMinutes: 60,
  emailChangeExpirationMinutes: 60,
  allowedImageTypes: [
    AdminSettingsAllowedImageTypesEnum.ImagePng,
    AdminSettingsAllowedImageTypesEnum.ImageJpeg,
    AdminSettingsAllowedImageTypesEnum.ImageWebp,
  ],
  maximumImageBytes: 10_485_760,
  perUserStorageQuotaBytes: 1_073_741_824,
  imageOrphanGraceHours: 24,
  noteVersionMaximumCount: 20,
  noteVersionRetentionDays: 30,
  userExportsEnabled: true,
  defaultTrashRetentionDays: 30,
  usersMayDisableAutoPurge: true,
  adminDeletionRetentionDays: 30,
  selfDeletionEnabled: true,
  commonPasswordCheckEnabled: true,
  passwordHistoryEnabled: false,
};

describe('AdminSettingsComponent', () => {
  let fixture: ComponentFixture<AdminSettingsComponent>;
  const api = {
    getAdminSettings: vi.fn(),
    updateAdminSettings: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.getAdminSettings.mockReturnValue(
      throwError(() => ({ error: { detail: 'Settings could not be loaded.' } })),
    );
    api.updateAdminSettings.mockReturnValue(of(settings));
    TestBed.configureTestingModule({
      providers: [{ provide: AdministrationService, useValue: api }],
    });
    fixture = TestBed.createComponent(AdminSettingsComponent);
    fixture.detectChanges();
  });

  it('does not submit editable defaults after the initial load fails', () => {
    const save = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;

    expect(save.disabled).toBe(true);
    save.click();
    fixture.componentInstance.save();

    expect(api.updateAdminSettings).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Settings could not be loaded.',
    );
  });
});
