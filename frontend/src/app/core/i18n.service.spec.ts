import { TestBed } from '@angular/core/testing';

import { I18nService } from './i18n.service';

describe('I18nService Batch 5 messages', () => {
  it('provides German share-warning and notes-settings messages', () => {
    const i18n = TestBed.inject(I18nService) as unknown as {
      set(language: 'de'): void;
      t(key: string): string | undefined;
    };
    i18n.set('de');

    expect(i18n.t('shareWarningTitle')).toBe('Vor dem Teilen');
    expect(i18n.t('shareImageWarning')).toContain('Bilder');
    expect(i18n.t('useLightTheme')?.toLowerCase()).toContain('hell');
    expect(i18n.t('openAccountSettings')).toContain('Kontoeinstellungen');
  });
});
