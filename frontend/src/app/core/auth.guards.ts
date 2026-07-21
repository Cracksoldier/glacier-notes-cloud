import { inject } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';

import { AuthStore } from './auth.store';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.session() ? true : inject(Router).createUrlTree(['/login']);
};

export const anonymousGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.session() ? inject(Router).createUrlTree(['/']) : true;
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  if (!auth.session()) {
    return inject(Router).createUrlTree(['/login']);
  }
  return auth.session()?.user.role === 'ADMIN' ? true : inject(Router).createUrlTree(['/']);
};
