import { inject } from '@angular/core';
import {
  type ActivatedRouteSnapshot,
  type CanActivateFn,
  Router,
  type UrlTree,
} from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Route data consumed by the guards:
 *   permissions?: string[] — any-of permission requirement (empty = none)
 */

/** Requires an authenticated session; redirects to /login otherwise. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.restoreSession().pipe(
    map((authenticated): boolean | UrlTree => {
      if (authenticated) {
        return true;
      }
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url },
      });
    }),
  );
};

/**
 * Requires authentication plus at least one of the permissions declared on the
 * route's data. Fails closed to a dedicated access-denied page rather than
 * silently redirecting to the dashboard.
 */
export const permissionGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const required = (route.data['permissions'] as string[] | undefined) ?? [];

  return auth.restoreSession().pipe(
    map((authenticated): boolean | UrlTree => {
      if (!authenticated) {
        return router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url },
        });
      }
      if (auth.hasAnyAuthority(required)) {
        return true;
      }
      return router.createUrlTree(['/access-denied']);
    }),
  );
};

/** Redirects already-authenticated users away from /login. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.restoreSession().pipe(
    map((authenticated): boolean | UrlTree =>
      authenticated ? router.createUrlTree(['/overview']) : true,
    ),
  );
};
