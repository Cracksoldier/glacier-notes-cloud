import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import {
  type AdminSettings,
  AdminSettingsAllowedImageTypesEnum,
  AdminSettingsDefaultLanguageEnum,
  AdminSettingsRestartRequiredSettingsEnum,
} from '../shared/generated-api/model/adminSettings';
import { AdminSettingsComponent } from './admin-settings.component';

const settings: AdminSettings = {
  instanceName: 'Glacier Notes',
  defaultLanguage: AdminSettingsDefaultLanguageEnum.En,
  allowedEmailDomains: [],
  invitationExpirationHours: 168,
  passwordResetExpirationMinutes: 60,
  emailChangeExpirationMinutes: 60,
  normalSessionDurationMinutes: 720,
  rememberSessionDurationMinutes: 43_200,
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
  publicBaseUrl: 'http://localhost:8080',
  smtpSenderName: 'Glacier Notes',
  smtpSenderAddress: 'noreply@localhost',
  auditRetentionDays: 365,
  operationalLogRetentionDays: 30,
  loginDelayThreshold: 5,
  loginLockThreshold: 10,
  loginLockMinutes: 15,
  commonPasswordCheckEnabled: true,
  passwordHistoryEnabled: false,
  restartRequiredSettings: [
    AdminSettingsRestartRequiredSettingsEnum.ImageStorageBackend,
    AdminSettingsRestartRequiredSettingsEnum.SmtpEnabled,
    AdminSettingsRestartRequiredSettingsEnum.BackupEnabled,
    AdminSettingsRestartRequiredSettingsEnum.MetricsEnabled,
  ],
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
