import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthStore } from '../core/auth.store';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <main class="page">
      <section>
        <p class="eyebrow">Private notes, on your infrastructure</p>
        <h1>Hello, {{ name() }}</h1>
        <p>
          Your secure cloud session is active. Note editing arrives in the next milestone.
        </p>
        <a href="/sessions" routerLink="/sessions">Manage signed-in devices</a>
      </section>
    </main>
  `,
  styles: `
    .page { display: grid; min-height: calc(100vh - 4.5rem); place-items: center; padding: 2rem; }
    section { width: min(46rem, 100%); }
    .eyebrow { color: #87c7d8; font-size: .78rem; font-weight: 700; letter-spacing: .13em; text-transform: uppercase; }
    h1 { margin: .5rem 0 1rem; color: #eef8fb; font-size: clamp(2.5rem, 8vw, 5rem); letter-spacing: -.055em; line-height: 1; }
    p { color: #abc3cc; line-height: 1.7; }
    a { color: #9adcea; }
  `,
})
export class HomeComponent {
  protected readonly auth = inject(AuthStore);
  protected readonly name = computed(() => {
    const user = this.auth.session()?.user;
    return user?.displayName || user?.username || '';
  });
}
