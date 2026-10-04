import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { AuthService } from '../auth/auth.service';

/**
 * Top-bar profile control — avatar trigger, identity block, organization
 * switcher (when multi-org), and sign-out.
 */
@Component({
  selector: 'app-profile-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatMenu, MatMenuItem, MatMenuTrigger],
  template: `
    <button
      type="button"
      class="trigger"
      [matMenuTriggerFor]="menu"
      aria-label="Account menu"
    >
      <span class="avatar" aria-hidden="true">{{ initials() }}</span>
      <span class="name">{{ user()?.displayName ?? user()?.email }}</span>
      <span class="material-icons chevron" aria-hidden="true">expand_more</span>
    </button>

    <mat-menu #menu="matMenu" class="profile-menu">
      <div class="identity">
        <div class="identity-name">{{ user()?.displayName ?? '—' }}</div>
        <div class="identity-email">{{ user()?.email }}</div>
        @if (auth.activeOrganization(); as org) {
          <div class="identity-org">{{ org.organizationName }}</div>
        }
      </div>

      @if ((user()?.organizations?.length ?? 0) > 1) {
        <div class="menu-section">
          <div class="section-label">Organization</div>
          @for (org of user()?.organizations ?? []; track org.organizationId) {
            <button
              mat-menu-item
              [class.selected]="org.organizationId === auth.activeOrganization()?.organizationId"
              (click)="auth.selectOrganization(org.organizationId)"
            >
              <span class="material-icons" aria-hidden="true">
                {{ org.organizationId === auth.activeOrganization()?.organizationId ? 'radio_button_checked' : 'radio_button_unchecked' }}
              </span>
              <span>{{ org.organizationName }}</span>
            </button>
          }
        </div>
      }

      <button mat-menu-item (click)="logout()">
        <span class="material-icons" aria-hidden="true">logout</span>
        <span>Sign out</span>
      </button>
    </mat-menu>
  `,
  styles: `
    .trigger {
      display: inline-flex;
      align-items: center;
      gap: var(--ely-space-2);
      height: 36px;
      padding: 0 var(--ely-space-2) 0 var(--ely-space-1);
      border: 1px solid transparent;
      border-radius: var(--ely-radius-sm);
      background: transparent;
      color: var(--ely-text);
      font: var(--ely-text-body);
      cursor: pointer;
    }
    .trigger:hover { background: var(--ely-bg-subtle); }

    .avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: var(--ely-accent-subtle);
      color: var(--ely-accent-strong);
      font: var(--ely-text-micro);
      font-weight: 600;
    }
    .name { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .chevron { font-size: 18px; width: 18px; height: 18px; color: var(--ely-text-3); }

    .identity {
      padding: var(--ely-space-3) var(--ely-space-4);
      border-bottom: 1px solid var(--ely-border);
      min-width: 220px;
    }
    .identity-name { font: var(--ely-text-body); font-weight: 600; }
    .identity-email { font: var(--ely-text-small); color: var(--ely-text-2); }
    .identity-org { font: var(--ely-text-micro); color: var(--ely-text-3); margin-top: 2px; }

    .menu-section { border-bottom: 1px solid var(--ely-border); }
    .section-label {
      font: var(--ely-text-caption);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ely-text-3);
      padding: var(--ely-space-2) var(--ely-space-4) 0;
    }
    .selected { color: var(--ely-accent-strong); }

    @media (max-width: 767px) {
      .name, .chevron { display: none; }
    }
  `,
})
export class ProfileMenuComponent {
  protected readonly auth = inject(AuthService);
  protected readonly user = this.auth.user;

  protected readonly initials = computed(() => {
    const name = this.user()?.displayName ?? this.user()?.email ?? '?';
    return name
      .split(/[\s@.]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  });

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
