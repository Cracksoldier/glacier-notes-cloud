import { Routes } from '@angular/router';
import { AccountSettingsComponent } from './account/account-settings.component';
import { AdminInvitationsComponent } from './admin/admin-invitations.component';
import { AdminSettingsComponent } from './admin/admin-settings.component';
import { AdminShellComponent } from './admin/admin-shell.component';
import { AdminStatusComponent } from './admin/admin-status.component';
import { AdminUserDetailComponent } from './admin/admin-user-detail.component';
import { AdminUsersComponent } from './admin/admin-users.component';
import { AcceptInvitationComponent } from './auth/accept-invitation.component';
import { ForgotPasswordComponent } from './auth/forgot-password.component';
import { LoginComponent } from './auth/login.component';
import { ResetPasswordComponent } from './auth/reset-password.component';
import { VerifyEmailChangeComponent } from './auth/verify-email-change.component';
import { adminGuard, anonymousGuard, authGuard } from './core/auth.guards';
import { NotesShellComponent } from './notes/notes-shell.component';
import { NotesViewMarkerComponent } from './notes/notes-view-marker.component';
import { SessionsComponent } from './sessions/sessions.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'notes' },
  {
    path: 'notes',
    component: NotesShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: NotesViewMarkerComponent },
      { path: 'notebooks/:notebookId', component: NotesViewMarkerComponent },
      { path: 'labels/:labelId', component: NotesViewMarkerComponent },
      { path: 'archive', component: NotesViewMarkerComponent },
      { path: 'trash', component: NotesViewMarkerComponent },
    ],
  },
  { path: 'login', component: LoginComponent, canActivate: [anonymousGuard] },
  {
    path: 'accept-invitation',
    component: AcceptInvitationComponent,
    canActivate: [anonymousGuard],
  },
  { path: 'forgot-password', component: ForgotPasswordComponent, canActivate: [anonymousGuard] },
  { path: 'reset-password', component: ResetPasswordComponent, canActivate: [anonymousGuard] },
  {
    path: 'verify-email-change',
    component: VerifyEmailChangeComponent,
    canActivate: [anonymousGuard],
  },
  { path: 'settings', component: AccountSettingsComponent, canActivate: [authGuard] },
  { path: 'sessions', component: SessionsComponent, canActivate: [authGuard] },
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'users' },
      { path: 'users', component: AdminUsersComponent },
      { path: 'users/:id', component: AdminUserDetailComponent },
      { path: 'invitations', component: AdminInvitationsComponent },
      { path: 'settings', component: AdminSettingsComponent },
      { path: 'status', component: AdminStatusComponent },
    ],
  },
  { path: '**', redirectTo: 'notes' },
];
