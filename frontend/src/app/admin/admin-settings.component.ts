import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminSettings } from '../shared/generated-api/model/adminSettings';
import { AdminSettingsUpdateAllowedImageTypesEnum } from '../shared/generated-api/model/adminSettingsUpdate';

@Component({
  selector: 'app-admin-settings',
  imports: [FormsModule],
  templateUrl: './admin-settings.component.html',
  styleUrl: './admin.css',
})
export class AdminSettingsComponent {
  private readonly api = inject(AdministrationService);
  readonly message = signal('');
  readonly error = signal('');
  readonly loading = signal(true);
  readonly loaded = signal(false);
  readonly saving = signal(false);
  domains = '';
  invitationHours = 168;
  resetMinutes = 60;
  emailChangeMinutes = 60;
  maximumImageMb = 10;
  quotaMb = 1024;
  orphanGraceHours = 24;
  noteVersionMaximumCount = 20;
  noteVersionRetentionDays = 30;
  userExportsEnabled = true;
  defaultTrashRetentionDays = 30;
  usersMayDisableAutoPurge = true;
  adminDeletionRetentionDays = 30;
  selfDeletionEnabled = true;
  commonPasswordCheckEnabled = true;
  passwordHistoryEnabled = false;
  imageTypes = new Set<string>(['image/png', 'image/jpeg', 'image/webp']);

  constructor() {
    this.api.getAdminSettings().subscribe({
      next: (value) => {
        this.apply(value);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: (failure) => {
        this.error.set(failure.error?.detail ?? 'Settings could not be loaded.');
        this.loading.set(false);
      },
    });
  }

  save(): void {
    if (!this.loaded() || this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    this.message.set('');
    const allowedEmailDomains = this.domains
      .split(/\s+/)
      .map((value) => value.trim())
      .filter(Boolean);
    this.api
      .updateAdminSettings({
        allowedEmailDomains,
        invitationExpirationHours: this.invitationHours,
        passwordResetExpirationMinutes: this.resetMinutes,
        emailChangeExpirationMinutes: this.emailChangeMinutes,
        allowedImageTypes: new Set([
          ...this.imageTypes,
        ] as AdminSettingsUpdateAllowedImageTypesEnum[]),
        maximumImageBytes: Math.round(this.maximumImageMb * 1048576),
        perUserStorageQuotaBytes: Math.round(this.quotaMb * 1048576),
        imageOrphanGraceHours: this.orphanGraceHours,
        noteVersionMaximumCount: this.noteVersionMaximumCount,
        noteVersionRetentionDays: this.noteVersionRetentionDays,
        userExportsEnabled: this.userExportsEnabled,
        defaultTrashRetentionDays: this.defaultTrashRetentionDays,
        usersMayDisableAutoPurge: this.usersMayDisableAutoPurge,
        adminDeletionRetentionDays: this.adminDeletionRetentionDays,
        selfDeletionEnabled: this.selfDeletionEnabled,
        commonPasswordCheckEnabled: this.commonPasswordCheckEnabled,
        passwordHistoryEnabled: this.passwordHistoryEnabled,
      })
      .subscribe({
        next: (value) => {
          this.apply(value);
          this.message.set('Instance settings saved.');
          this.saving.set(false);
        },
        error: (failure) => {
          this.error.set(failure.error?.detail ?? 'Settings could not be saved.');
          this.saving.set(false);
        },
      });
  }

  toggleImageType(type: string, checked: boolean): void {
    const next = new Set(this.imageTypes);
    if (checked) next.add(type);
    else if (next.size > 1) next.delete(type);
    this.imageTypes = next;
  }

  private apply(value: AdminSettings): void {
    this.domains = value.allowedEmailDomains.join('\n');
    this.invitationHours = value.invitationExpirationHours;
    this.resetMinutes = value.passwordResetExpirationMinutes;
    this.emailChangeMinutes = value.emailChangeExpirationMinutes;
    this.maximumImageMb = value.maximumImageBytes / 1048576;
    this.quotaMb = value.perUserStorageQuotaBytes / 1048576;
    this.orphanGraceHours = value.imageOrphanGraceHours;
    this.noteVersionMaximumCount = value.noteVersionMaximumCount;
    this.noteVersionRetentionDays = value.noteVersionRetentionDays;
    this.userExportsEnabled = value.userExportsEnabled;
    this.defaultTrashRetentionDays = value.defaultTrashRetentionDays;
    this.usersMayDisableAutoPurge = value.usersMayDisableAutoPurge;
    this.adminDeletionRetentionDays = value.adminDeletionRetentionDays;
    this.selfDeletionEnabled = value.selfDeletionEnabled;
    this.commonPasswordCheckEnabled = value.commonPasswordCheckEnabled;
    this.passwordHistoryEnabled = value.passwordHistoryEnabled;
    this.imageTypes = new Set(value.allowedImageTypes);
  }
}
