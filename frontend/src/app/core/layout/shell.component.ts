import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  type OnInit,
  inject,
  signal,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ContextService } from '../context/context.service';
import { BreadcrumbComponent } from './breadcrumb.component';
import { NavRailComponent } from './nav-rail.component';
import { ProfileMenuComponent } from './profile-menu.component';

/**
 * Application shell: persistent left rail + top bar with breadcrumb,
 * context, and profile. Responsive per UX_SPEC §3.4:
 *   ≥1024px  — 232px expanded rail (collapses to 64px icon rail ≤1439px…
 *              spec collapses at ≤1023; here expanded for ≥1024)
 *   768–1023 — 64px icon rail
 *   <768px   — rail hidden; menu button opens a drawer overlay
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, NavRailComponent, BreadcrumbComponent, ProfileMenuComponent],
  template: `
    <div class="shell" [class.rail-collapsed]="isTablet()" [class.drawer-open]="drawerOpen()">
      @if (isMobile()) {
        @if (drawerOpen()) {
          <div class="backdrop" (click)="drawerOpen.set(false)" aria-hidden="true"></div>
          <aside class="drawer">
            <app-nav-rail [collapsed]="false" />
          </aside>
        }
      } @else {
        <aside class="rail-slot">
          <app-nav-rail [collapsed]="isTablet()" />
        </aside>
      }

      <div class="main">
        <header class="topbar">
          <div class="topbar-left">
            @if (isMobile()) {
              <button
                type="button"
                class="menu-button"
                aria-label="Open navigation"
                (click)="drawerOpen.set(true)"
              >
                <span class="material-icons" aria-hidden="true">menu</span>
              </button>
            }
            <app-breadcrumb />
          </div>
          <div class="topbar-right">
            <app-profile-menu />
          </div>
        </header>

        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .shell {
      display: flex;
      height: 100vh;
      overflow: hidden;
    }

    .rail-slot {
      width: var(--ely-rail-width);
      flex-shrink: 0;
      transition: width 160ms ease;
    }
    .rail-collapsed .rail-slot { width: var(--ely-rail-collapsed); }

    .main {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .topbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--ely-space-4);
      height: var(--ely-topbar-height);
      padding: 0 var(--ely-space-6);
      background: var(--ely-surface);
      border-bottom: 1px solid var(--ely-border);
      flex-shrink: 0;
    }
    .topbar-left {
      display: flex;
      align-items: center;
      gap: var(--ely-space-3);
      min-width: 0;
    }
    .topbar-right { display: flex; align-items: center; gap: var(--ely-space-2); }

    .menu-button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border: none;
      border-radius: var(--ely-radius-sm);
      background: transparent;
      color: var(--ely-text-2);
      cursor: pointer;
    }
    .menu-button:hover { background: var(--ely-bg-subtle); }

    .content {
      flex: 1;
      overflow-y: auto;
      padding: var(--ely-space-6);
    }
    .content > * {
      max-width: var(--ely-content-max);
      margin: 0 auto;
      display: block;
    }

    .backdrop {
      position: fixed;
      inset: 0;
      background: rgba(32, 31, 29, 0.32);
      z-index: 90;
    }
    .drawer {
      position: fixed;
      top: 0;
      left: 0;
      bottom: 0;
      width: var(--ely-rail-width);
      z-index: 100;
      box-shadow: var(--ely-shadow-float);
    }

    @media (max-width: 767px) {
      .topbar { padding: 0 var(--ely-space-4); }
      .content { padding: var(--ely-space-4); }
    }
  `,
})
export class ShellComponent implements OnInit {
  private readonly context = inject(ContextService);

  protected readonly isMobile = signal(false);
  protected readonly isTablet = signal(false);
  protected readonly drawerOpen = signal(false);

  ngOnInit(): void {
    this.context.initFromRoute();
    this.onResize();
  }

  @HostListener('window:resize')
  protected onResize(): void {
    const width = window.innerWidth;
    this.isMobile.set(width < 768);
    this.isTablet.set(width >= 768 && width <= 1023);
    if (!this.isMobile()) {
      this.drawerOpen.set(false);
    }
  }
}
