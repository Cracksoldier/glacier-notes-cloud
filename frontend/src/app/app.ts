import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthStore } from './core/auth.store';
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

  protected readonly applicationState = signal<ApplicationState>('loading');

  constructor() {
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
          if (authenticated && this.router.url === '/login') {
            void this.router.navigate(['/']);
          } else if (!authenticated && this.router.url !== '/login') {
            void this.router.navigate(['/login']);
          }
        });
      },
      error: () => this.applicationState.set('error'),
    });
  }
}
