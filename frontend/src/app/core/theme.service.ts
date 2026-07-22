import { computed, Injectable, signal } from '@angular/core';

export type GlacierTheme = 'dark' | 'light';

const STORAGE_KEY = 'glacier-notes-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<GlacierTheme>(this.readTheme());
  readonly dark = computed(() => this.theme() === 'dark');

  constructor() {
    this.apply(this.theme());
  }

  toggle(): void {
    this.set(this.dark() ? 'light' : 'dark');
  }

  set(theme: GlacierTheme): void {
    this.theme.set(theme);
    localStorage.setItem(STORAGE_KEY, theme);
    this.apply(theme);
  }

  private readTheme(): GlacierTheme {
    return localStorage.getItem(STORAGE_KEY) === 'light' ? 'light' : 'dark';
  }

  private apply(theme: GlacierTheme): void {
    document.documentElement.classList.toggle('theme-dark', theme === 'dark');
    document.documentElement.classList.toggle('theme-light', theme === 'light');
    document.documentElement.style.colorScheme = theme;
  }
}
