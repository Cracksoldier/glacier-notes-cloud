import { Routes } from '@angular/router';
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
import { adminGuard, anonymousGuard, authGuard } from './core/auth.guards';
import { HomeComponent } from './home/home.component';
import { SessionsComponent } from './sessions/sessions.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [authGuard] },
  { path: 'login', component: LoginComponent, canActivate: [anonymousGuard] },
  {
    path: 'accept-invitation',
    component: AcceptInvitationComponent,
    canActivate: [anonymousGuard],
  },
  { path: 'forgot-password', component: ForgotPasswordComponent, canActivate: [anonymousGuard] },
  { path: 'reset-password', component: ResetPasswordComponent, canActivate: [anonymousGuard] },
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
  { path: '**', redirectTo: '' },
];
