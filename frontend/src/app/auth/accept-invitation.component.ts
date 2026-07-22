import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthenticationService } from '../shared/generated-api/api/authentication.service';
import { InvitationAcceptanceRequestLanguageEnum } from '../shared/generated-api/model/invitationAcceptanceRequest';
import type { InvitationInspection } from '../shared/generated-api/model/invitationInspection';

@Component({
  selector: 'app-accept-invitation',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './accept-invitation.component.html',
  styleUrl: './public-lifecycle.css',
})
export class AcceptInvitationComponent {
  private readonly api = inject(AuthenticationService);
  readonly inspection = signal<InvitationInspection | null>(null);
  readonly busy = signal(false);
  readonly completed = signal(false);
  readonly error = signal('');
  token = '';
  username = '';
  displayName = '';
  password = '';

  constructor(route: ActivatedRoute) {
    this.token = route.snapshot.queryParamMap.get('token') ?? '';
    if (this.token) {
      history.replaceState({}, '', '/accept-invitation');
      this.inspect();
    }
  }

  inspect(): void {
    this.error.set('');
    this.api.inspectInvitation({ token: this.token }).subscribe({
      next: (value) => {
        this.inspection.set(value);
        this.username = value.proposedUsername ?? '';
        this.displayName = value.displayName ?? '';
      },
      error: (failure) =>
        this.error.set(failure.error?.detail ?? 'This invitation is invalid or expired.'),
    });
  }

  accept(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .acceptInvitation({
        token: this.token,
        username: this.username,
        displayName: this.displayName || undefined,
        password: this.password,
        language: navigator.language.toLowerCase().startsWith('de')
          ? InvitationAcceptanceRequestLanguageEnum.De
          : InvitationAcceptanceRequestLanguageEnum.En,
      })
      .subscribe({
        next: () => {
          this.password = '';
          this.busy.set(false);
          this.completed.set(true);
        },
        error: (failure) => {
          this.password = '';
          this.busy.set(false);
          this.error.set(failure.error?.detail ?? 'Account activation failed.');
        },
      });
  }
}
