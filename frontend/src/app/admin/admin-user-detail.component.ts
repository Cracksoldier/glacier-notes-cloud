import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../core/auth.store';
import { I18nService } from '../core/i18n.service';
import { ProblemService } from '../core/problem.service';
import { StepUpPrompt } from '../core/step-up';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import { AdminDeletionRequestModeEnum } from '../shared/generated-api/model/adminDeletionRequest';
import type { AdminUser } from '../shared/generated-api/model/adminUser';
import { AdminUserUpdateRoleEnum } from '../shared/generated-api/model/adminUserUpdate';
import { ImportApplyRequestStrategyEnum } from '../shared/generated-api/model/importApplyRequest';
import type { ResetLink } from '../shared/generated-api/model/resetLink';
import type { TransferJob } from '../shared/generated-api/model/transferJob';
import { StepUpCodeComponent } from '../shared/step-up-code.component';

export type PendingAction = 'reset' | 'schedule' | 'delete' | 'clear-second-factor';

@Component({
  selector: 'app-admin-user-detail',
  imports: [FormsModule, StepUpCodeComponent],
  templateUrl: './admin-user-detail.component.html',
  styleUrl: './admin.css',
})
export class AdminUserDetailComponent {
  private readonly api = inject(AdministrationService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly problems = inject(ProblemService);
  protected readonly i18n = inject(I18nService);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';
  readonly user = signal<AdminUser | null>(null);
  readonly reset = signal<ResetLink | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  readonly importJob = signal<TransferJob | null>(null);
  readonly importBusy = signal(false);
  readonly ImportStrategy = ImportApplyRequestStrategyEnum;
  readonly pending = signal<PendingAction | null>(null);
  readonly prompt = new StepUpPrompt();
  currentPassword = '';
  confirmation = '';
  username = '';
  email = '';
  displayName = '';
  role: AdminUserUpdateRoleEnum = AdminUserUpdateRoleEnum.User;
  private importOperationSequence = 0;

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
    if (!confirm(this.i18n.t('adminUserConfirmSave'))) return;
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
          this.message.set(this.i18n.t('adminAccountUpdated'));
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

  begin(action: PendingAction): void {
    this.pending.set(action);
    this.prompt.clear();
    this.currentPassword = '';
    this.confirmation = '';
    this.error.set('');
  }

  cancelPending(): void {
    this.pending.set(null);
    this.prompt.clear();
    this.currentPassword = '';
    this.confirmation = '';
  }

  submitPending(): void {
    this.error.set('');
    switch (this.pending()) {
      case 'reset':
        this.resetPassword();
        break;
      case 'schedule':
        this.scheduleDeletion();
        break;
      case 'delete':
        this.deleteImmediately();
        break;
      case 'clear-second-factor':
        this.clearSecondFactor();
        break;
    }
  }

  private clearSecondFactor(): void {
    this.api
      .clearUserMfa(this.id, {
        currentPassword: this.currentPassword,
        code: this.prompt.value(),
      })
      .subscribe({
        next: (value) => {
          this.user.set(value);
          this.message.set(this.i18n.t('adminActionCompleted'));
          this.cancelPending();
          // The clear revoked every session of the target, including this one when it is our own.
          this.checkSelf();
        },
        error: (failure) => this.fail(failure),
      });
  }

  private resetPassword(): void {
    this.api
      .createAdministrativePasswordReset(this.id, {
        currentPassword: this.currentPassword,
        code: this.prompt.value(),
      })
      .subscribe({
        next: (value) => {
          this.reset.set(value);
          this.cancelPending();
        },
        error: (failure) => this.fail(failure),
      });
  }

  private scheduleDeletion(): void {
    this.api
      .scheduleUserDeletion(this.id, {
        mode: AdminDeletionRequestModeEnum.Retained,
        currentPassword: this.currentPassword,
        code: this.prompt.value(),
      })
      .subscribe({
        next: (value) => {
          this.user.set(value);
          this.message.set(this.i18n.t('adminAccountDeletionScheduled'));
          this.cancelPending();
          this.checkSelf();
        },
        error: (failure) => this.fail(failure),
      });
  }

  private deleteImmediately(): void {
    this.api
      .scheduleUserDeletion(this.id, {
        mode: AdminDeletionRequestModeEnum.Immediate,
        confirmation: this.confirmation,
        currentPassword: this.currentPassword,
        code: this.prompt.value(),
      })
      .subscribe({
        next: () => {
          this.cancelPending();
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
        this.message.set(this.i18n.t('adminAccountDeletionCanceled'));
      },
      error: (failure) => this.fail(failure),
    });
  }

  async inspectImport(file: File | null): Promise<void> {
    if (!file) return;
    const operation = ++this.importOperationSequence;
    this.importBusy.set(true);
    this.error.set('');
    try {
      const created = await firstValueFrom(this.api.createAdminImport(this.id, file));
      if (!this.activeImport(operation)) return;
      const inspected = await this.poll(operation, created);
      if (!inspected || !this.activeImport(operation)) return;
      this.importJob.set(inspected);
      if (inspected.state === 'READY' && !inspected.hasConflicts) {
        await this.applyImport(ImportApplyRequestStrategyEnum.Preserve);
      }
    } catch (failure) {
      if (this.activeImport(operation)) {
        this.fail(failure);
      }
    } finally {
      if (this.activeImport(operation)) this.importBusy.set(false);
    }
  }

  async applyImport(strategy: ImportApplyRequestStrategyEnum): Promise<void> {
    const current = this.importJob();
    if (!current) return;
    const operation = ++this.importOperationSequence;
    this.importBusy.set(true);
    try {
      const queued = await firstValueFrom(this.api.applyAdminImport(current.id, { strategy }));
      if (!this.activeImport(operation)) return;
      const completed = await this.poll(operation, queued);
      if (!completed || !this.activeImport(operation)) return;
      this.importJob.set(completed);
      if (completed.state === 'SUCCEEDED') {
        this.message.set(this.i18n.t('adminBlindImportCompleted'));
        this.load();
      }
    } catch (failure) {
      if (this.activeImport(operation)) {
        this.fail(failure);
      }
    } finally {
      if (this.activeImport(operation)) this.importBusy.set(false);
    }
  }

  async cancelImport(): Promise<void> {
    const operation = ++this.importOperationSequence;
    const current = this.importJob();
    this.importBusy.set(true);
    this.error.set('');
    try {
      if (current) await firstValueFrom(this.api.cancelAdminImport(current.id));
      if (this.activeImport(operation)) this.importJob.set(null);
    } catch (failure) {
      if (this.activeImport(operation)) {
        this.fail(failure);
      }
    } finally {
      if (this.activeImport(operation)) this.importBusy.set(false);
    }
  }

  private async poll(operation: number, initial: TransferJob): Promise<TransferJob | null> {
    if (!this.activeImport(operation)) return null;
    let current = initial;
    this.importJob.set(current);
    while (this.activeImport(operation) && ['QUEUED', 'RUNNING'].includes(current.state)) {
      await new Promise((resolve) => setTimeout(resolve, 500));
      if (!this.activeImport(operation)) return null;
      const loaded = await firstValueFrom(this.api.getAdminImport(current.id));
      if (!this.activeImport(operation)) return null;
      current = loaded;
      this.importJob.set(current);
    }
    return this.activeImport(operation) ? current : null;
  }

  private activeImport(operation: number): boolean {
    return operation === this.importOperationSequence;
  }

  copy(value: string): void {
    void navigator.clipboard.writeText(value);
    this.message.set(this.i18n.t('commonCopied'));
  }

  private action(kind: 'activate' | 'deactivate' | 'unlock' | 'sessions'): void {
    if (
      (kind === 'deactivate' || kind === 'sessions') &&
      !confirm(this.i18n.t('adminUserActionRevokesAccess'))
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
        this.message.set(this.i18n.t('adminActionCompleted'));
        const self = this.auth.session()?.user.id === this.id;
        if (kind === 'deactivate' || kind === 'sessions') this.checkSelf();
        if (!self) this.load();
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

  private fail(failure: unknown): void {
    // The code field lives in the confirmation panel. Without one open there is no queued request
    // to attach a code to, so a refusal has to be reported rather than turned into a prompt.
    if (this.pending() && this.prompt.handle(failure)) return;
    this.error.set(this.problems.message(failure));
  }
}
