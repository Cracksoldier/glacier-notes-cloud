import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthStore } from '../core/auth.store';
import { I18nService } from '../core/i18n.service';
import { PreferencesService } from '../core/preferences.service';
import { CurrentUserService } from '../shared/generated-api/api/currentUser.service';
import type { UserProfile } from '../shared/generated-api/model/userProfile';
import type { UserSettings } from '../shared/generated-api/model/userSettings';

@Component({
  selector: 'app-account-settings',
  imports: [FormsModule, RouterLink],
  templateUrl: './account-settings.component.html',
  styleUrl: './account-settings.component.css',
})
export class AccountSettingsComponent {
  private readonly api = inject(CurrentUserService);
  private readonly preferences = inject(PreferencesService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);

  readonly profile = signal<UserProfile | null>(null);
  readonly settings = signal<UserSettings | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  readonly busy = signal(false);

  username = '';
  displayName = '';
  newEmail = '';
  emailPassword = '';
  currentPassword = '';
  newPassword = '';
  deletionPassword = '';
  theme: 'dark' | 'light' = 'dark';
  language: 'en' | 'de' = 'en';
  moveCheckedToBottom = false;
  trashAutoPurgeDays = 30;

  constructor() {
    void this.load();
  }

  async load(): Promise<void> {
    try {
      const [profile, settings] = await Promise.all([
        firstValueFrom(this.api.getCurrentUserProfile()),
        this.preferences.load(),
      ]);
      this.profile.set(profile);
      this.settings.set(settings);
      this.username = profile.username;
      this.displayName = profile.displayName ?? '';
      this.theme = settings.theme === 'light' ? 'light' : 'dark';
      this.language = settings.language === 'de' ? 'de' : 'en';
      this.moveCheckedToBottom = settings.moveCheckedToBottom;
      this.trashAutoPurgeDays = settings.trashAutoPurgeDays;
    } catch (failure) {
      this.fail(failure);
    }
  }

  async saveProfile(): Promise<void> {
    await this.run(async () => {
      const profile = await firstValueFrom(
        this.api.updateCurrentUserProfile({
          username: this.username,
          displayName: this.displayName || undefined,
        }),
      );
      this.profile.set(profile);
      this.message.set(this.i18n.t('profileSaved'));
      this.auth.restore().subscribe();
    });
  }

  async savePreferences(): Promise<void> {
    await this.run(async () => {
      const settings = await this.preferences.update({
        theme: this.theme,
        language: this.language,
        moveCheckedToBottom: this.moveCheckedToBottom,
        trashAutoPurgeDays: this.trashAutoPurgeDays,
      });
      this.settings.set(settings);
      this.message.set(this.i18n.t('settingsSaved'));
    });
  }

  async changePassword(): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(
        this.api.changeCurrentUserPassword({
          currentPassword: this.currentPassword,
          newPassword: this.newPassword,
        }),
      );
      this.clearSecrets();
      this.auth.clear();
      await this.router.navigate(['/login']);
    });
  }

  async requestEmailChange(): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(
        this.api.requestCurrentUserEmailChange({
          currentPassword: this.emailPassword,
          newEmail: this.newEmail,
        }),
      );
      this.emailPassword = '';
      this.message.set(this.i18n.t('verificationSent'));
    });
  }

  async deleteAccount(): Promise<void> {
    if (!window.confirm(this.i18n.t('deletionConfirm'))) return;
    await this.run(async () => {
      await firstValueFrom(this.api.deleteCurrentUser({ currentPassword: this.deletionPassword }));
      this.clearSecrets();
      this.auth.clear();
      await this.router.navigate(['/login']);
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    try {
      await action();
    } catch (failure) {
      this.fail(failure);
    } finally {
      this.busy.set(false);
    }
  }

  private fail(failure: unknown): void {
    const value = failure as { error?: { detail?: string } };
    this.error.set(value.error?.detail ?? this.i18n.t('changeFailed'));
  }

  private clearSecrets(): void {
    this.currentPassword = '';
    this.newPassword = '';
    this.emailPassword = '';
    this.deletionPassword = '';
  }
}
