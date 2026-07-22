import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthStore } from './core/auth.store';
import { I18nService } from './core/i18n.service';
import { PreferencesService } from './core/preferences.service';
import { SetupComponent } from './setup/setup.component';
import { SetupService } from './shared/generated-api/api/setup.service';

type ApplicationState = 'loading' | 'setup' | 'ready' | 'error';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet, SetupComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly setupApi = inject(SetupService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthStore);
  protected readonly i18n = inject(I18nService);
  private readonly preferences = inject(PreferencesService);

  protected readonly applicationState = signal<ApplicationState>('loading');
  protected readonly notesActive = signal(false);

  constructor() {
    this.notesActive.set(this.router.url.startsWith('/notes'));
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => this.notesActive.set(event.urlAfterRedirects.startsWith('/notes')));
    this.loadSetupStatus();
  }

  protected setupCompleted(): void {
    this.auth.clear();
    this.applicationState.set('ready');
    void this.router.navigate(['/login']);
  }

  protected retry(): void {
    this.applicationState.set('loading');
    this.loadSetupStatus();
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => {
        this.auth.clear();
        void this.router.navigate(['/login']);
      },
    });
  }

  private loadSetupStatus(): void {
    this.setupApi.getSetupStatus().subscribe({
      next: (response) => {
        if (response.setupRequired) {
          this.applicationState.set('setup');
          return;
        }
        this.auth.restore().subscribe((authenticated) => {
          this.applicationState.set('ready');
          if (authenticated) void this.preferences.load().catch(() => undefined);
          if (authenticated && this.router.url === '/login') {
            void this.router.navigate(['/notes']);
          } else if (!authenticated && !this.isPublicRoute()) {
            void this.router.navigate(['/login']);
          }
        });
      },
      error: () => this.applicationState.set('error'),
    });
  }

  private isPublicRoute(): boolean {
    return [
      '/login',
      '/accept-invitation',
      '/forgot-password',
      '/reset-password',
      '/verify-email-change',
    ].some((path) => this.router.url.startsWith(path));
  }
}
