import { type Routes } from '@angular/router';
import { authGuard, guestGuard, permissionGuard } from './core/auth/auth.guard';
import { ShellComponent } from './core/layout/shell.component';

/**
 * Route map mirrors UX_SPEC §2. Every business route is lazy-loaded, guarded
 * by auth + any-of permissions, and carries a static breadcrumb label.
 * `placeholder: true` routes render PagePlaceholderComponent until their
 * business pages are implemented.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
    title: 'Sign in — Enerlytics',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [permissionGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      {
        path: 'overview',
        loadComponent: () =>
          import('./features/overview/overview.component').then(
            (m) => m.OverviewComponent,
          ),
        data: { breadcrumb: 'Overview', permissions: ['analytics:read'] },
        title: 'Overview — Enerlytics',
      },
      {
        path: 'live',
        loadComponent: () =>
          import('./features/live-energy/live-energy.component').then(
            (m) => m.LiveEnergyComponent,
          ),
        data: { breadcrumb: 'Live Energy', permissions: ['telemetry:read'] },
        title: 'Live Energy — Enerlytics',
      },
      {
        path: 'alerts',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Alerts',
          title: 'Alerts',
          icon: 'notifications',
          description:
            'Alert inbox, rule management, and lifecycle actions — coming in the alerts phase.',
          permissions: ['alert:read'],
        },
        title: 'Alerts — Enerlytics',
      },
      {
        path: 'analytics',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Analytics',
          title: 'Analytics',
          icon: 'query_stats',
          description:
            'Consumption trends, demand analysis, and anomaly exploration.',
          permissions: ['analytics:read'],
        },
        title: 'Analytics — Enerlytics',
      },
      {
        path: 'carbon',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Carbon',
          title: 'Carbon',
          icon: 'eco',
          description:
            'Emissions tracking, intensity trends, and per-facility carbon breakdowns.',
          permissions: ['carbon:read'],
        },
        title: 'Carbon — Enerlytics',
      },
      {
        path: 'costs',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Costs',
          title: 'Costs',
          icon: 'payments',
          description:
            'Energy spend by site, tariff breakdowns, and baseline comparisons.',
          permissions: ['analytics:read'],
        },
        title: 'Costs — Enerlytics',
      },
      {
        path: 'forecast',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Forecast',
          title: 'Forecast',
          icon: 'trending_up',
          description:
            'Deterministic 24-hour and 7-day consumption forecasts with accuracy measurement.',
          permissions: ['forecast:read'],
        },
        title: 'Forecast — Enerlytics',
      },
      {
        path: 'facilities',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Facilities',
          title: 'Facilities',
          icon: 'domain',
          description: 'Sites, buildings, and their energy characteristics.',
          permissions: ['site:read'],
        },
        title: 'Facilities — Enerlytics',
      },
      {
        path: 'meters',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Meters',
          title: 'Meters',
          icon: 'speed',
          description: 'Meter inventory, health, and channel configuration.',
          permissions: ['meter:read'],
        },
        title: 'Meters — Enerlytics',
      },
      {
        path: 'reports',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Reports',
          title: 'Reports',
          icon: 'description',
          description: 'Scheduled and on-demand energy and carbon reports.',
          permissions: ['report:read'],
        },
        title: 'Reports — Enerlytics',
      },
      {
        path: 'sustainability',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Sustainability',
          title: 'Sustainability',
          icon: 'flag',
          description:
            'Emission targets, renewable share, and progress tracking.',
          permissions: ['carbon:read'],
        },
        title: 'Sustainability — Enerlytics',
      },
      {
        path: 'admin',
        loadComponent: () =>
          import('./shared/page-placeholder.component').then(
            (m) => m.PagePlaceholderComponent,
          ),
        data: {
          breadcrumb: 'Administration',
          title: 'Administration',
          icon: 'settings',
          description:
            'Organization settings, users, roles, and audit history.',
          permissions: ['organization:read', 'user:read'],
        },
        title: 'Administration — Enerlytics',
      },
      {
        path: 'access-denied',
        loadComponent: () =>
          import('./shared/status-pages.component').then(
            (m) => m.AccessDeniedComponent,
          ),
        data: { breadcrumb: 'Access denied' },
        title: 'Access denied — Enerlytics',
      },
    ],
  },
  {
    path: '**',
    loadComponent: () =>
      import('./shared/status-pages.component').then(
        (m) => m.NotFoundComponent,
      ),
    title: 'Not found — Enerlytics',
  },
];
