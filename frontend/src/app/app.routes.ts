import { Routes } from '@angular/router';
import { AdminStatusComponent } from './admin/admin-status.component';
import { LoginComponent } from './auth/login.component';
import { adminGuard, anonymousGuard, authGuard } from './core/auth.guards';
import { HomeComponent } from './home/home.component';
import { SessionsComponent } from './sessions/sessions.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [authGuard] },
  { path: 'login', component: LoginComponent, canActivate: [anonymousGuard] },
  { path: 'sessions', component: SessionsComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminStatusComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '' },
];
