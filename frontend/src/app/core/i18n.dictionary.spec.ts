import { __messagesForTest } from './i18n.service';

describe('i18n dictionary', () => {
  it('has symmetrical keys for every supported language', () => {
    const enKeys = Object.keys(__messagesForTest.en).sort();
    const deKeys = Object.keys(__messagesForTest.de).sort();
    const missingInDe = enKeys.filter((key) => !deKeys.includes(key));
    const missingInEn = deKeys.filter((key) => !enKeys.includes(key));
    expect(missingInDe).toEqual([]);
    expect(missingInEn).toEqual([]);
  });

  it('has non-empty translations for every key in every language', () => {
    for (const [language, entries] of Object.entries(__messagesForTest)) {
      for (const [key, value] of Object.entries(entries)) {
        if (!value) {
          throw new Error(`Missing translation for ${language}.${key}`);
        }
      }
    }
  });
});
