import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
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
  domains = '';
  invitationHours = 168;
  resetMinutes = 60;
  maximumImageMb = 10;
  quotaMb = 1024;
  orphanGraceHours = 24;
  noteVersionMaximumCount = 20;
  noteVersionRetentionDays = 30;
  userExportsEnabled = true;
  imageTypes = new Set<string>(['image/png', 'image/jpeg', 'image/webp']);

  constructor() {
    this.api.getAdminSettings().subscribe((value) => {
      this.domains = value.allowedEmailDomains.join('\n');
      this.invitationHours = value.invitationExpirationHours;
      this.resetMinutes = value.passwordResetExpirationMinutes;
      this.maximumImageMb = value.maximumImageBytes / 1048576;
      this.quotaMb = value.perUserStorageQuotaBytes / 1048576;
      this.orphanGraceHours = value.imageOrphanGraceHours;
      this.noteVersionMaximumCount = value.noteVersionMaximumCount;
      this.noteVersionRetentionDays = value.noteVersionRetentionDays;
      this.userExportsEnabled = value.userExportsEnabled;
      this.imageTypes = new Set(value.allowedImageTypes);
    });
  }

  save(): void {
    const allowedEmailDomains = this.domains
      .split(/\s+/)
      .map((value) => value.trim())
      .filter(Boolean);
    this.api
      .updateAdminSettings({
        allowedEmailDomains,
        invitationExpirationHours: this.invitationHours,
        passwordResetExpirationMinutes: this.resetMinutes,
        allowedImageTypes: new Set([
          ...this.imageTypes,
        ] as AdminSettingsUpdateAllowedImageTypesEnum[]),
        maximumImageBytes: Math.round(this.maximumImageMb * 1048576),
        perUserStorageQuotaBytes: Math.round(this.quotaMb * 1048576),
        imageOrphanGraceHours: this.orphanGraceHours,
        noteVersionMaximumCount: this.noteVersionMaximumCount,
        noteVersionRetentionDays: this.noteVersionRetentionDays,
        userExportsEnabled: this.userExportsEnabled,
      })
      .subscribe({
        next: (value) => {
          this.domains = value.allowedEmailDomains.join('\n');
          this.message.set('Instance settings saved.');
        },
        error: (failure) => this.error.set(failure.error?.detail ?? 'Settings could not be saved.'),
      });
  }

  toggleImageType(type: string, checked: boolean): void {
    const next = new Set(this.imageTypes);
    if (checked) next.add(type);
    else if (next.size > 1) next.delete(type);
    this.imageTypes = next;
  }
}
