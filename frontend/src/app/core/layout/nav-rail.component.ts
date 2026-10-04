import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatTooltip } from '@angular/material/tooltip';
import { AuthService } from '../auth/auth.service';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Any-of permission requirement — mirrors seeded backend authorities. */
  permissions: string[];
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const NAV: NavGroup[] = [
  {
    label: 'Monitor',
    items: [
      { label: 'Overview', icon: 'dashboard', route: '/overview', permissions: ['analytics:read'] },
      { label: 'Live Energy', icon: 'bolt', route: '/live', permissions: ['telemetry:read'] },
      { label: 'Alerts', icon: 'notifications', route: '/alerts', permissions: ['alert:read'] },
    ],
  },
  {
    label: 'Analyze',
    items: [
      { label: 'Analytics', icon: 'query_stats', route: '/analytics', permissions: ['analytics:read'] },
      { label: 'Carbon', icon: 'eco', route: '/carbon', permissions: ['carbon:read'] },
      { label: 'Costs', icon: 'payments', route: '/costs', permissions: ['analytics:read'] },
      { label: 'Forecast', icon: 'trending_up', route: '/forecast', permissions: ['forecast:read'] },
    ],
  },
  {
    label: 'Manage',
    items: [
      { label: 'Facilities', icon: 'domain', route: '/facilities', permissions: ['site:read'] },
      { label: 'Meters', icon: 'speed', route: '/meters', permissions: ['meter:read'] },
    ],
  },
  {
    label: 'Govern',
    items: [
      { label: 'Reports', icon: 'description', route: '/reports', permissions: ['report:read'] },
      { label: 'Sustainability', icon: 'flag', route: '/sustainability', permissions: ['carbon:read'] },
      {
        label: 'Administration',
        icon: 'settings',
        route: '/admin',
        permissions: ['organization:read', 'user:read'],
      },
    ],
  },
];

/**
 * Left navigation rail. Renders only items the user has permission for;
 * collapses to a 64px icon rail at ≤1023px (driven by the shell's class).
 */
@Component({
  selector: 'app-nav-rail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, MatTooltip],
  template: `
    <nav class="rail" aria-label="Primary">
      <div class="brand" [class.collapsed]="collapsed()">
        <span class="brand-mark" aria-hidden="true">E</span>
        @if (!collapsed()) {
          <span class="brand-name">Enerlytics</span>
        }
      </div>

      @for (group of visibleGroups(); track group.label) {
        <div class="group">
          @if (!collapsed()) {
            <div class="group-label">{{ group.label }}</div>
          }
          @for (item of group.items; track item.route) {
            <a
              class="item"
              [routerLink]="item.route"
              routerLinkActive="active"
              [matTooltip]="collapsed() ? item.label : ''"
              matTooltipPosition="right"
            >
              <span class="material-icons icon" aria-hidden="true">{{ item.icon }}</span>
              @if (!collapsed()) {
                <span class="item-label">{{ item.label }}</span>
              }
            </a>
          }
        </div>
      }
    </nav>
  `,
  styles: `
    :host { display: block; height: 100%; }

    .rail {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--ely-surface);
      border-right: 1px solid var(--ely-border);
      padding: var(--ely-space-3) var(--ely-space-2);
      overflow-y: auto;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: var(--ely-space-2);
      height: 44px;
      padding: 0 var(--ely-space-2);
      margin-bottom: var(--ely-space-4);
    }
    .brand.collapsed { justify-content: center; padding: 0; }
    .brand-mark {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: var(--ely-radius-md);
      background: var(--ely-accent);
      color: #fff;
      font: var(--ely-text-section);
      flex-shrink: 0;
    }
    .brand-name {
      font: var(--ely-text-section);
      letter-spacing: 0.01em;
    }

    .group { margin-bottom: var(--ely-space-4); }
    .group-label {
      font: var(--ely-text-caption);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ely-text-3);
      padding: 0 var(--ely-space-3);
      margin-bottom: var(--ely-space-1);
    }

    .item {
      position: relative;
      display: flex;
      align-items: center;
      gap: var(--ely-space-3);
      height: 40px;
      padding: 0 var(--ely-space-3);
      border-radius: var(--ely-radius-sm);
      color: var(--ely-text-2);
      text-decoration: none;
      font: var(--ely-text-body);
    }
    .item:hover { background: var(--ely-bg-subtle); color: var(--ely-text); }
    .item.active {
      background: var(--ely-accent-subtle);
      color: var(--ely-accent-strong);
      font-weight: 500;
    }
    .item.active::before {
      content: '';
      position: absolute;
      left: calc(-1 * var(--ely-space-2));
      top: 6px;
      bottom: 6px;
      width: 3px;
      background: var(--ely-accent);
      border-radius: 0 2px 2px 0;
    }
    .icon { font-size: 18px; width: 18px; height: 18px; flex-shrink: 0; }
    .item.active .icon { color: var(--ely-accent); }
  `,
})
export class NavRailComponent {
  private readonly auth = inject(AuthService);

  readonly collapsed = input(false);

  readonly visibleGroups = computed(() =>
    NAV.map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        this.auth.hasAnyAuthority(item.permissions),
      ),
    })).filter((group) => group.items.length > 0),
  );
}
