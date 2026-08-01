import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthStore } from '../core/auth.store';
import { I18nService } from '../core/i18n.service';
import { PreferencesService } from '../core/preferences.service';
import { ProblemService } from '../core/problem.service';
import { StepUpPrompt } from '../core/step-up';
import { CurrentUserService } from '../shared/generated-api/api/currentUser.service';
import type { UserProfile } from '../shared/generated-api/model/userProfile';
import type { UserSettings } from '../shared/generated-api/model/userSettings';
import { StepUpCodeComponent } from '../shared/step-up-code.component';
import { TwoFactorCardComponent } from './two-factor-card.component';

@Component({
  selector: 'app-account-settings',
  imports: [FormsModule, RouterLink, StepUpCodeComponent, TwoFactorCardComponent],
  templateUrl: './account-settings.component.html',
  styleUrl: './account-settings.component.css',
})
export class AccountSettingsComponent {
  private readonly api = inject(CurrentUserService);
  private readonly preferences = inject(PreferencesService);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly problems = inject(ProblemService);
  protected readonly i18n = inject(I18nService);

  readonly emailPrompt = new StepUpPrompt();
  readonly deletionPrompt = new StepUpPrompt();

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
          code: this.emailPrompt.value(),
        }),
      );
      this.emailPassword = '';
      this.emailPrompt.clear();
      this.message.set(this.i18n.t('verificationSent'));
    }, this.emailPrompt);
  }

  async deleteAccount(): Promise<void> {
    // Already confirmed once; a retry only adds the code the server asked for.
    if (!this.deletionPrompt.open() && !window.confirm(this.i18n.t('deletionConfirm'))) return;
    await this.run(async () => {
      await firstValueFrom(
        this.api.deleteCurrentUser({
          currentPassword: this.deletionPassword,
          code: this.deletionPrompt.value(),
        }),
      );
      this.clearSecrets();
      this.auth.clear();
      await this.router.navigate(['/login']);
    }, this.deletionPrompt);
  }

  private async run(action: () => Promise<void>, prompt?: StepUpPrompt): Promise<void> {
    this.busy.set(true);
    this.error.set('');
    this.message.set('');
    try {
      await action();
    } catch (failure) {
      if (!prompt?.handle(failure)) this.fail(failure);
    } finally {
      this.busy.set(false);
    }
  }

  private fail(failure: unknown): void {
    this.error.set(this.problems.message(failure));
  }

  private clearSecrets(): void {
    this.currentPassword = '';
    this.newPassword = '';
    this.emailPassword = '';
    this.deletionPassword = '';
    this.emailPrompt.clear();
    this.deletionPrompt.clear();
  }
}
