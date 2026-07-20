import { Component, inject, signal } from '@angular/core';

import { SetupComponent } from './setup/setup.component';
import { SetupService } from './shared/generated-api/api/setup.service';
import { SystemService } from './shared/generated-api/api/system.service';

type ConnectionState = 'connecting' | 'connected' | 'error';
type ApplicationState = 'loading' | 'setup' | 'ready' | 'error';

@Component({
  selector: 'app-root',
  imports: [SetupComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly setupApi = inject(SetupService);
  private readonly systemApi = inject(SystemService);

  protected readonly applicationState = signal<ApplicationState>('loading');
  protected readonly connectionState = signal<ConnectionState>('connecting');
  protected readonly serverTime = signal<string | null>(null);

  constructor() {
    this.loadSetupStatus();
  }

  protected setupCompleted(): void {
    this.connect();
  }

  protected retry(): void {
    this.applicationState.set('loading');
    this.loadSetupStatus();
  }

  private loadSetupStatus(): void {
    this.setupApi.getSetupStatus().subscribe({
      next: (response) => {
        if (response.setupRequired) {
          this.applicationState.set('setup');
        } else {
          this.connect();
        }
      },
      error: () => this.applicationState.set('error'),
    });
  }

  private connect(): void {
    this.applicationState.set('ready');
    this.connectionState.set('connecting');
    this.systemApi.ping().subscribe({
      next: (response) => {
        this.serverTime.set(response.serverTime);
        this.connectionState.set('connected');
      },
      error: () => this.connectionState.set('error'),
    });
  }
}
