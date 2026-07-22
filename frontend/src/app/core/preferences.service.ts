import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CurrentUserService } from '../shared/generated-api/api/currentUser.service';
import type { UserSettings } from '../shared/generated-api/model/userSettings';
import {
  type UserSettingsUpdate,
  UserSettingsUpdateLanguageEnum,
  UserSettingsUpdateThemeEnum,
} from '../shared/generated-api/model/userSettingsUpdate';
import { I18nService } from './i18n.service';
import { ThemeService } from './theme.service';

@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly api = inject(CurrentUserService);
  private readonly theme = inject(ThemeService);
  private readonly i18n = inject(I18nService);

  readonly value = signal<UserSettings | null>(null);

  async load(): Promise<UserSettings> {
    const value = await firstValueFrom(this.api.getCurrentUserSettings());
    this.apply(value);
    return value;
  }

  async update(update: {
    theme?: 'dark' | 'light';
    language?: 'en' | 'de';
    moveCheckedToBottom?: boolean;
    trashAutoPurgeDays?: number;
  }): Promise<UserSettings> {
    const request: UserSettingsUpdate = {
      moveCheckedToBottom: update.moveCheckedToBottom,
      trashAutoPurgeDays: update.trashAutoPurgeDays,
      theme:
        update.theme === undefined
          ? undefined
          : update.theme === 'light'
            ? UserSettingsUpdateThemeEnum.Light
            : UserSettingsUpdateThemeEnum.Dark,
      language:
        update.language === undefined
          ? undefined
          : update.language === 'de'
            ? UserSettingsUpdateLanguageEnum.De
            : UserSettingsUpdateLanguageEnum.En,
    };
    const value = await firstValueFrom(this.api.updateCurrentUserSettings(request));
    this.apply(value);
    return value;
  }

  private apply(value: UserSettings): void {
    this.value.set(value);
    this.theme.set(value.theme === 'light' ? 'light' : 'dark');
    this.i18n.set(value.language === 'de' ? 'de' : 'en');
  }
}
