import { Injectable, signal } from '@angular/core';

export type GlacierLanguage = 'en' | 'de';

const messages = {
  en: {
    notes: 'Notes',
    archive: 'Archive',
    trash: 'Trash',
    settings: 'Settings',
    sessions: 'Sessions',
    administration: 'Administration',
    signOut: 'Sign out',
    search: 'Search notes…',
    notebooks: 'Notebooks',
    labels: 'Labels',
    shareEmail: 'Share by email',
    shareWarningTitle: 'Before sharing',
    shareImageWarning: 'Images cannot be attached to the email.',
    shareLengthWarning: 'This note may be too long for your browser or mail application.',
    sharePrivacy:
      'Note content is passed directly to your email application and is never sent by the server.',
    shareCancel: 'Cancel',
    exportMarkdownInstead: 'Export Markdown instead',
    continueToEmail: 'Continue to email',
    useLightTheme: 'Use light theme',
    useDarkTheme: 'Use dark theme',
    settingsSynchronized: 'Theme changes are synchronized with your account.',
    openAccountSettings: 'Open account & settings',
    close: 'Close',
    accountNavigation: 'Account navigation',
    accountSettings: 'Account & settings',
    accountSettingsIntro:
      'Manage your identity, security, language, appearance, and retention preferences.',
    profile: 'Profile',
    usernameUnique: 'Your username is unique without regard to letter case.',
    username: 'Username',
    displayName: 'Display name',
    email: 'Email',
    saveProfile: 'Save profile',
    profileSaved: 'Profile saved.',
    appearanceBehavior: 'Appearance & behavior',
    theme: 'Theme',
    dark: 'Dark',
    light: 'Light',
    language: 'Language',
    moveChecked: 'Move checked checklist items to the bottom',
    purgeTrashDays: 'Automatically purge trash after (days)',
    keepTrashHint: 'Use 0 to keep trashed notes until you delete them manually.',
    savePreferences: 'Save preferences',
    settingsSaved: 'Settings saved.',
    changePassword: 'Change password',
    passwordRevokesSessions:
      'All sessions, including this one, are revoked after a successful change.',
    currentPassword: 'Current password',
    newPassword: 'New password',
    changeEmail: 'Change email',
    emailPendingHint: 'The old address remains active until you verify the new one.',
    newEmail: 'New email',
    sendVerification: 'Send verification',
    verificationSent: 'Verification sent. Your current email remains active until confirmation.',
    emailUnavailable: 'Email changes are unavailable because this instance has no SMTP service.',
    deleteAccount: 'Delete account',
    deletionWarning:
      'This immediately and permanently removes notebooks, notes, checklist items, labels, image binaries, user settings, sessions, security tokens, version history, and import/export state. It cannot be undone.',
    deletionConfirm:
      'Permanently delete your notebooks, notes, checklist items, labels, images, user settings, sessions, version history, and import/export state? This is immediate and cannot be undone.',
    deletePermanently: 'Permanently delete account',
    loadingSettings: 'Loading account settings…',
    changeFailed: 'The change could not be saved.',
  },
  de: {
    notes: 'Notizen',
    archive: 'Archiv',
    trash: 'Papierkorb',
    settings: 'Einstellungen',
    sessions: 'Sitzungen',
    administration: 'Administration',
    signOut: 'Abmelden',
    search: 'Notizen durchsuchen…',
    notebooks: 'Notizbücher',
    labels: 'Schlagwörter',
    shareEmail: 'Per E-Mail teilen',
    shareWarningTitle: 'Vor dem Teilen',
    shareImageWarning: 'Bilder können nicht an die E-Mail angehängt werden.',
    shareLengthWarning: 'Diese Notiz ist möglicherweise zu lang für Browser oder E-Mail-Programm.',
    sharePrivacy:
      'Der Inhalt wird direkt an dein E-Mail-Programm übergeben und nie vom Server versendet.',
    shareCancel: 'Abbrechen',
    exportMarkdownInstead: 'Stattdessen Markdown exportieren',
    continueToEmail: 'Weiter zur E-Mail',
    useLightTheme: 'Helles Farbschema verwenden',
    useDarkTheme: 'Dunkles Farbschema verwenden',
    settingsSynchronized: 'Änderungen am Farbschema werden mit deinem Konto synchronisiert.',
    openAccountSettings: 'Kontoeinstellungen öffnen',
    close: 'Schließen',
    accountNavigation: 'Kontonavigation',
    accountSettings: 'Konto & Einstellungen',
    accountSettingsIntro: 'Verwalte Identität, Sicherheit, Sprache, Darstellung und Aufbewahrung.',
    profile: 'Profil',
    usernameUnique: 'Dein Benutzername ist unabhängig von Groß- und Kleinschreibung eindeutig.',
    username: 'Benutzername',
    displayName: 'Anzeigename',
    email: 'E-Mail',
    saveProfile: 'Profil speichern',
    profileSaved: 'Profil gespeichert.',
    appearanceBehavior: 'Darstellung & Verhalten',
    theme: 'Farbschema',
    dark: 'Dunkel',
    light: 'Hell',
    language: 'Sprache',
    moveChecked: 'Erledigte Checklistenpunkte nach unten verschieben',
    purgeTrashDays: 'Papierkorb automatisch leeren nach (Tagen)',
    keepTrashHint: 'Mit 0 bleiben Notizen bis zum manuellen Löschen im Papierkorb.',
    savePreferences: 'Einstellungen speichern',
    settingsSaved: 'Einstellungen gespeichert.',
    changePassword: 'Passwort ändern',
    passwordRevokesSessions:
      'Nach der Änderung werden alle Sitzungen einschließlich dieser beendet.',
    currentPassword: 'Aktuelles Passwort',
    newPassword: 'Neues Passwort',
    changeEmail: 'E-Mail-Adresse ändern',
    emailPendingHint: 'Die alte Adresse bleibt bis zur Bestätigung der neuen Adresse aktiv.',
    newEmail: 'Neue E-Mail-Adresse',
    sendVerification: 'Bestätigung senden',
    verificationSent: 'Bestätigung gesendet. Die aktuelle E-Mail-Adresse bleibt vorerst aktiv.',
    emailUnavailable:
      'E-Mail-Änderungen sind nicht verfügbar, da kein SMTP-Dienst eingerichtet ist.',
    deleteAccount: 'Konto löschen',
    deletionWarning:
      'Notizbücher, Notizen, Checklisten, Schlagwörter, Bilder, Benutzereinstellungen, Sitzungen, Sicherheitstoken, Versionsverlauf sowie Import- und Exportstatus werden sofort und endgültig gelöscht.',
    deletionConfirm:
      'Notizbücher, Notizen, Checklisten, Schlagwörter, Bilder, Benutzereinstellungen, Sitzungen, Versionsverlauf sowie Import- und Exportstatus endgültig löschen? Dies kann nicht rückgängig gemacht werden.',
    deletePermanently: 'Konto endgültig löschen',
    loadingSettings: 'Kontoeinstellungen werden geladen…',
    changeFailed: 'Die Änderung konnte nicht gespeichert werden.',
  },
} as const;

export type MessageKey = keyof (typeof messages)['en'];

@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly language = signal<GlacierLanguage>(this.browserLanguage());

  constructor() {
    document.documentElement.lang = this.language();
  }

  set(language: GlacierLanguage): void {
    this.language.set(language);
    document.documentElement.lang = language;
  }

  t(key: MessageKey): string {
    return messages[this.language()][key];
  }

  formatDate(value: string | Date): string {
    return new Intl.DateTimeFormat(this.language() === 'de' ? 'de-AT' : 'en', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  private browserLanguage(): GlacierLanguage {
    return navigator.language.toLowerCase().startsWith('de') ? 'de' : 'en';
  }
}
