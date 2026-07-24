import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdministrationService } from '../shared/generated-api/api/administration.service';

@Component({
  selector: 'app-admin-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin.css',
})
export class AdminShellComponent {
  private readonly api = inject(AdministrationService);
  readonly backupEnabled = signal(false);

  constructor() {
    this.api.getAdminStatus().subscribe({
      next: (status) => this.backupEnabled.set(status.backupEnabled),
    });
  }
}
