import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { I18nService } from '../core/i18n.service';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminUser } from '../shared/generated-api/model/adminUser';

@Component({
  selector: 'app-admin-users',
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin.css',
})
export class AdminUsersComponent {
  private readonly api = inject(AdministrationService);
  protected readonly i18n = inject(I18nService);
  readonly users = signal<AdminUser[]>([]);
  readonly error = signal('');
  query = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.api.listUsers(this.query || undefined).subscribe({
      next: (page) => this.users.set(page.items),
      error: (failure) =>
        this.error.set(failure.error?.detail ?? this.i18n.t('adminUsersLoadFailed')),
    });
  }
}
