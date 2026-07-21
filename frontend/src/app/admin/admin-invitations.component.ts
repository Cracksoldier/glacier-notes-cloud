import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdministrationService } from '../shared/generated-api/api/administration.service';
import { InvitationCreateRequestRoleEnum } from '../shared/generated-api/model/invitationCreateRequest';
import type { InvitationDelivery } from '../shared/generated-api/model/invitationDelivery';
import type { InvitationSummary } from '../shared/generated-api/model/invitationSummary';

@Component({
  selector: 'app-admin-invitations',
  imports: [FormsModule],
  templateUrl: './admin-invitations.component.html',
  styleUrl: './admin.css',
})
export class AdminInvitationsComponent {
  private readonly api = inject(AdministrationService);
  readonly invitations = signal<InvitationSummary[]>([]);
  readonly delivery = signal<InvitationDelivery | null>(null);
  readonly message = signal('');
  readonly error = signal('');
  email = '';
  proposedUsername = '';
  displayName = '';
  role: InvitationCreateRequestRoleEnum = InvitationCreateRequestRoleEnum.User;
  status: '' | 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED' = 'PENDING';

  constructor() {
    this.load();
  }

  create(): void {
    this.delivery.set(null);
    this.api
      .createInvitation({
        email: this.email,
        proposedUsername: this.proposedUsername || undefined,
        displayName: this.displayName || undefined,
        role: this.role,
      })
      .subscribe({
        next: (value) => {
          this.delivery.set(value);
          this.message.set(
            value.delivery === 'EMAIL_SENT'
              ? 'Invitation email sent.'
              : 'Copy the one-time activation link.',
          );
          this.email = '';
          this.proposedUsername = '';
          this.displayName = '';
          this.load();
        },
        error: (failure) => this.fail(failure),
      });
  }

  resend(id: string): void {
    this.api.resendInvitation(id).subscribe({
      next: (value) => {
        this.delivery.set(value);
        this.message.set(
          value.delivery === 'EMAIL_SENT'
            ? 'Replacement invitation sent.'
            : 'Copy the replacement link.',
        );
        this.load();
      },
      error: (failure) => this.fail(failure),
    });
  }

  revoke(id: string): void {
    if (!confirm('Revoke this invitation?')) return;
    this.api.revokeInvitation(id).subscribe({
      next: () => this.load(),
      error: (failure) => this.fail(failure),
    });
  }

  copy(value: string | undefined): void {
    if (value) void navigator.clipboard.writeText(value);
  }

  load(): void {
    this.api.listInvitations(this.status || undefined).subscribe({
      next: (page) => this.invitations.set(page.items),
      error: (failure) => this.fail(failure),
    });
  }

  private fail(failure: { error?: { detail?: string } }): void {
    this.error.set(failure.error?.detail ?? 'The invitation action failed.');
  }
}
