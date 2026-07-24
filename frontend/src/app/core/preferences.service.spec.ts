import { TestBed } from '@angular/core/testing';
import { of, Subject, TimeoutError } from 'rxjs';

import { CurrentUserService } from '../shared/generated-api/api/currentUser.service';
import {
  type UserSettings,
  UserSettingsLanguageEnum,
  UserSettingsThemeEnum,
} from '../shared/generated-api/model/userSettings';
import { I18nService } from './i18n.service';
import { PreferencesService } from './preferences.service';
import { ThemeService } from './theme.service';

function settings(theme: 'dark' | 'light', language: 'en' | 'de'): UserSettings {
  return {
    theme: theme === 'light' ? UserSettingsThemeEnum.Light : UserSettingsThemeEnum.Dark,
    language: language === 'de' ? UserSettingsLanguageEnum.De : UserSettingsLanguageEnum.En,
    moveCheckedToBottom: false,
    trashAutoPurgeDays: 30,
    trashAutoPurgeMayBeDisabled: true,
  };
}

describe('PreferencesService', () => {
  const api = {
    getCurrentUserSettings: vi.fn(),
    updateCurrentUserSettings: vi.fn(),
  };
  const theme = { set: vi.fn() };
  const i18n = { set: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: CurrentUserService, useValue: api },
        { provide: ThemeService, useValue: theme },
        { provide: I18nService, useValue: i18n },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('coalesces concurrent preference loads', async () => {
    const response = new Subject<UserSettings>();
    api.getCurrentUserSettings.mockReturnValue(response);
    const service = TestBed.inject(PreferencesService);

    const first = service.load();
    const second = service.load();

    await Promise.resolve();
    expect(api.getCurrentUserSettings).toHaveBeenCalledTimes(1);
    response.next(settings('dark', 'en'));
    response.complete();
    await expect(Promise.all([first, second])).resolves.toHaveLength(2);
  });

  it('serializes an update behind an older load so stale settings cannot win', async () => {
    const loadResponse = new Subject<UserSettings>();
    const updateResponse = new Subject<UserSettings>();
    api.getCurrentUserSettings.mockReturnValue(loadResponse);
    api.updateCurrentUserSettings.mockReturnValue(updateResponse);
    const service = TestBed.inject(PreferencesService);

    const load = service.load();
    const update = service.update({ theme: 'light', language: 'de' });

    await Promise.resolve();
    expect(api.updateCurrentUserSettings).not.toHaveBeenCalled();
    loadResponse.next(settings('dark', 'en'));
    loadResponse.complete();
    await load;
    await Promise.resolve();
    expect(api.updateCurrentUserSettings).toHaveBeenCalledTimes(1);

    updateResponse.next(settings('light', 'de'));
    updateResponse.complete();
    await update;

    expect(service.value()).toEqual(settings('light', 'de'));
    expect(theme.set).toHaveBeenLastCalledWith('light');
    expect(i18n.set).toHaveBeenLastCalledWith('de');
  });

  it('advances the preference queue when an API request never responds', async () => {
    vi.useFakeTimers();
    api.getCurrentUserSettings.mockReturnValue(new Subject<UserSettings>());
    api.updateCurrentUserSettings.mockReturnValue(of(settings('light', 'de')));
    const service = TestBed.inject(PreferencesService);

    const load = service.load();
    const loadFailure = load.catch((failure: unknown) => failure);
    const update = service.update({ theme: 'light', language: 'de' });

    await Promise.resolve();
    expect(api.updateCurrentUserSettings).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(30_000);

    expect(await loadFailure).toBeInstanceOf(TimeoutError);
    await expect(update).resolves.toEqual(settings('light', 'de'));
    expect(api.updateCurrentUserSettings).toHaveBeenCalledTimes(1);
  });
});
