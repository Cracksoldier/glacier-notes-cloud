import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthStore } from '../core/auth.store';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import type { AdminUser } from '../shared/generated-api/model/adminUser';
import { AdminUserUpdateRoleEnum } from '../shared/generated-api/model/adminUserUpdate';
import type { ResetLink } from '../shared/generated-api/model/resetLink';

@Component({
  selector: 'app-admin-user-detail',
  imports: [FormsModule],
  templateUrl: './admin-user-detail.component.html',
  styleUrl: './admin.css',
})
export class AdminUserDetailComponent {
  private readonly api = inject(AdministrationService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  readonly user = signal<AdminUser | null>(null);
  readonly reset = signal<ResetLink | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  username = '';
  email = '';
  displayName = '';
  role: AdminUserUpdateRoleEnum = AdminUserUpdateRoleEnum.User;

  constructor() {
    this.load();
  }

  load(): void {
    this.api.getUser(this.id).subscribe({
      next: (value) => {
        this.user.set(value);
        this.username = value.username;
        this.email = value.email;
        this.displayName = value.displayName ?? '';
        this.role =
          value.role === 'ADMIN' ? AdminUserUpdateRoleEnum.Admin : AdminUserUpdateRoleEnum.User;
      },
      error: (failure) => this.fail(failure),
    });
  }

  save(): void {
    if (!confirm('Apply these account changes? Role changes revoke all sessions.')) return;
    this.api
      .updateUser(this.id, {
        username: this.username,
        email: this.email,
        displayName: this.displayName || undefined,
        role: this.role,
      })
      .subscribe({
        next: (value) => {
          this.user.set(value);
          this.message.set('Account updated.');
          this.checkSelf(value);
        },
        error: (failure) => this.fail(failure),
      });
  }

  activate(): void {
    this.action('activate');
  }

  deactivate(): void {
    this.action('deactivate');
  }

  unlock(): void {
    this.action('unlock');
  }

  revokeSessions(): void {
    this.action('sessions');
  }

  resetPassword(): void {
    this.api.createAdministrativePasswordReset(this.id).subscribe({
      next: (value) => this.reset.set(value),
      error: (failure) => this.fail(failure),
    });
  }

  copy(value: string): void {
    void navigator.clipboard.writeText(value);
    this.message.set('Copied.');
  }

  private action(kind: 'activate' | 'deactivate' | 'unlock' | 'sessions'): void {
    if (
      (kind === 'deactivate' || kind === 'sessions') &&
      !confirm('This action immediately revokes access. Continue?')
    ) {
      return;
    }
    const request =
      kind === 'activate'
        ? this.api.activateUser(this.id)
        : kind === 'deactivate'
          ? this.api.deactivateUser(this.id)
          : kind === 'unlock'
            ? this.api.unlockUser(this.id)
            : this.api.revokeUserSessions(this.id);
    request.subscribe({
      next: () => {
        this.message.set('Action completed.');
        if (kind === 'deactivate' || kind === 'sessions') this.checkSelf();
        else this.load();
      },
      error: (failure) => this.fail(failure),
    });
  }

  private checkSelf(updated?: AdminUser): void {
    if (this.auth.session()?.user.id === this.id && updated?.role !== 'ADMIN') {
      this.auth.clear();
      void this.router.navigate(['/login']);
    }
  }

  private fail(failure: { error?: { detail?: string } }): void {
    this.error.set(failure.error?.detail ?? 'The action could not be completed.');
  }
}
