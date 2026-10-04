import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../core/ui/empty-state.component';

@Component({
  selector: 'app-access-denied',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, RouterLink],
  template: `
    <div class="status-page">
      <app-empty-state heading="Access denied" icon="lock">
        You don't have permission to view this page. Contact your organization
        administrator if you believe this is a mistake.
        <div slot="actions">
          <a routerLink="/overview" class="link">Back to overview</a>
        </div>
      </app-empty-state>
    </div>
  `,
  styles: `
    .status-page { display: flex; justify-content: center; padding-top: var(--ely-space-16); }
    .link { color: var(--ely-accent); font: var(--ely-text-body); }
  `,
})
export class AccessDeniedComponent {}

@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent, RouterLink],
  template: `
    <div class="status-page">
      <app-empty-state heading="Page not found" icon="travel_explore">
        The page you're looking for doesn't exist or may have been moved.
        <div slot="actions">
          <a routerLink="/overview" class="link">Back to overview</a>
        </div>
      </app-empty-state>
    </div>
  `,
  styles: `
    .status-page { display: flex; justify-content: center; padding-top: var(--ely-space-16); }
    .link { color: var(--ely-accent); font: var(--ely-text-body); }
  `,
})
export class NotFoundComponent {}
