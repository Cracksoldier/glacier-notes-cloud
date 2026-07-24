import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

import { CurrentUserService } from '../shared/generated-api/api/currentUser.service';
import type { UserSettings } from '../shared/generated-api/model/userSettings';
import {
  type UserSettingsUpdate,
  UserSettingsUpdateLanguageEnum,
  UserSettingsUpdateThemeEnum,
} from '../shared/generated-api/model/userSettingsUpdate';
import { I18nService } from './i18n.service';
import { ThemeService } from './theme.service';

const REQUEST_TIMEOUT_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly api = inject(CurrentUserService);
  private readonly theme = inject(ThemeService);
  private readonly i18n = inject(I18nService);

  readonly value = signal<UserSettings | null>(null);
  private queue: Promise<void> = Promise.resolve();
  private loading: Promise<UserSettings> | null = null;

  async load(): Promise<UserSettings> {
    if (this.loading) return this.loading;
    const operation = this.enqueue(async () => {
      const value = await firstValueFrom(
        this.api.getCurrentUserSettings().pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      this.apply(value);
      return value;
    });
    this.loading = operation;
    operation.then(
      () => {
        if (this.loading === operation) this.loading = null;
      },
      () => {
        if (this.loading === operation) this.loading = null;
      },
    );
    return operation;
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
    return this.enqueue(async () => {
      const value = await firstValueFrom(
        this.api.updateCurrentUserSettings(request).pipe(timeout(REQUEST_TIMEOUT_MS)),
      );
      this.apply(value);
      return value;
    });
  }

  private enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.queue.then(operation, operation);
    this.queue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }

  private apply(value: UserSettings): void {
    this.value.set(value);
    this.theme.set(value.theme === 'light' ? 'light' : 'dark');
    this.i18n.set(value.language === 'de' ? 'de' : 'en');
  }
}
