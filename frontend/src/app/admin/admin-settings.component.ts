import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdministrationService } from '../shared/generated-api/api/administration.service';

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

  constructor() {
    this.api.getAdminSettings().subscribe((value) => {
      this.domains = value.allowedEmailDomains.join('\n');
      this.invitationHours = value.invitationExpirationHours;
      this.resetMinutes = value.passwordResetExpirationMinutes;
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
      })
      .subscribe({
        next: (value) => {
          this.domains = value.allowedEmailDomains.join('\n');
          this.message.set('Access settings saved.');
        },
        error: (failure) => this.error.set(failure.error?.detail ?? 'Settings could not be saved.'),
      });
  }
}
