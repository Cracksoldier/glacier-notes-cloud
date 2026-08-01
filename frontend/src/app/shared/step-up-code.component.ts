import { Component, inject, model } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { I18nService } from '../core/i18n.service';

@Component({
  selector: 'app-step-up-code',
  imports: [FormsModule],
  template: `
    <p class="hint">{{ i18n.t('stepUpPrompt') }}</p>
    <label
      >{{ i18n.t('stepUpCodeLabel') }}
      <input
        name="stepUpCode"
        inputmode="numeric"
        autocomplete="one-time-code"
        autocapitalize="characters"
        spellcheck="false"
        required
        [ngModel]="code()"
        (ngModelChange)="code.set($event)"
      >
      <small>{{ i18n.t('stepUpCodeHint') }}</small></label
    >
  `,
})
export class StepUpCodeComponent {
  protected readonly i18n = inject(I18nService);
  readonly code = model('');
}
