import { Component, inject, signal } from '@angular/core';

import { SystemService } from './shared/generated-api/api/system.service';

type ConnectionState = 'connecting' | 'connected' | 'error';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly systemApi = inject(SystemService);

  protected readonly connectionState = signal<ConnectionState>('connecting');
  protected readonly serverTime = signal<string | null>(null);

  constructor() {
    this.systemApi.ping().subscribe({
      next: (response) => {
        this.serverTime.set(response.serverTime);
        this.connectionState.set('connected');
      },
      error: () => this.connectionState.set('error'),
    });
  }
}
