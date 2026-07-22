import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../core/auth.store';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import { AdminDeletionRequestModeEnum } from '../shared/generated-api/model/adminDeletionRequest';
import type { AdminUser } from '../shared/generated-api/model/adminUser';
import { AdminUserUpdateRoleEnum } from '../shared/generated-api/model/adminUserUpdate';
import { ImportApplyRequestStrategyEnum } from '../shared/generated-api/model/importApplyRequest';
import type { ResetLink } from '../shared/generated-api/model/resetLink';
import type { TransferJob } from '../shared/generated-api/model/transferJob';

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
  readonly importJob = signal<TransferJob | null>(null);
  readonly importBusy = signal(false);
  readonly ImportStrategy = ImportApplyRequestStrategyEnum;
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

  scheduleDeletion(): void {
    if (!confirm('Schedule this account for retained deletion? Access is revoked immediately.'))
      return;
    this.api
      .scheduleUserDeletion(this.id, { mode: AdminDeletionRequestModeEnum.Retained })
      .subscribe({
        next: (value) => {
          this.user.set(value);
          this.message.set('Account deletion scheduled. It can be restored until the due time.');
          this.checkSelf();
        },
        error: (failure) => this.fail(failure),
      });
  }

  deleteImmediately(): void {
    const user = this.user();
    if (!user) return;
    const confirmation = prompt(`Type ${user.username} to permanently delete this account now.`);
    if (confirmation === null) return;
    this.api
      .scheduleUserDeletion(this.id, {
        mode: AdminDeletionRequestModeEnum.Immediate,
        confirmation,
      })
      .subscribe({
        next: () => {
          if (this.auth.session()?.user.id === this.id) {
            this.auth.clear();
            void this.router.navigate(['/login']);
          } else {
            void this.router.navigate(['/admin/users']);
          }
        },
        error: (failure) => this.fail(failure),
      });
  }

  restoreDeletion(): void {
    this.api.restoreUserDeletion(this.id).subscribe({
      next: (value) => {
        this.user.set(value);
        this.message.set('Scheduled deletion canceled.');
      },
      error: (failure) => this.fail(failure),
    });
  }

  async inspectImport(file: File | null): Promise<void> {
    if (!file) return;
    this.importBusy.set(true);
    this.error.set('');
    try {
      const created = await firstValueFrom(this.api.createAdminImport(this.id, file));
      const inspected = await this.poll(created);
      this.importJob.set(inspected);
      if (inspected.state === 'READY' && !inspected.hasConflicts) {
        await this.applyImport(ImportApplyRequestStrategyEnum.Preserve);
      }
    } catch (failure) {
      this.fail(failure as { error?: { detail?: string } });
    } finally {
      this.importBusy.set(false);
    }
  }

  async applyImport(strategy: ImportApplyRequestStrategyEnum): Promise<void> {
    const current = this.importJob();
    if (!current) return;
    this.importBusy.set(true);
    try {
      const queued = await firstValueFrom(this.api.applyAdminImport(current.id, { strategy }));
      const completed = await this.poll(queued);
      this.importJob.set(completed);
      if (completed.state === 'SUCCEEDED') {
        this.message.set('Blind import completed and was recorded in the audit log.');
        this.load();
      }
    } catch (failure) {
      this.fail(failure as { error?: { detail?: string } });
    } finally {
      this.importBusy.set(false);
    }
  }

  async cancelImport(): Promise<void> {
    const current = this.importJob();
    if (current) await firstValueFrom(this.api.cancelAdminImport(current.id));
    this.importJob.set(null);
    this.importBusy.set(false);
  }

  private async poll(initial: TransferJob): Promise<TransferJob> {
    let current = initial;
    this.importJob.set(current);
    while (['QUEUED', 'RUNNING'].includes(current.state)) {
      await new Promise((resolve) => setTimeout(resolve, 500));
      current = await firstValueFrom(this.api.getAdminImport(current.id));
      this.importJob.set(current);
    }
    return current;
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
