import { inject } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthStore } from './auth.store';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!auth.restored()) {
    return auth
      .ensureRestored()
      .pipe(map(() => (auth.session() ? true : router.createUrlTree(['/login']))));
  }
  return auth.session() ? true : router.createUrlTree(['/login']);
};

export const anonymousGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!auth.restored()) {
    return auth
      .ensureRestored()
      .pipe(map(() => (auth.session() ? router.createUrlTree(['/']) : true)));
  }
  return auth.session() ? router.createUrlTree(['/']) : true;
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);
  if (!auth.restored()) {
    return auth.ensureRestored().pipe(
      map(() => {
        if (!auth.session()) return router.createUrlTree(['/login']);
        return auth.session()?.user.role === 'ADMIN' ? true : router.createUrlTree(['/']);
      }),
    );
  }
  if (!auth.session()) {
    return router.createUrlTree(['/login']);
  }
  return auth.session()?.user.role === 'ADMIN' ? true : router.createUrlTree(['/']);
};
