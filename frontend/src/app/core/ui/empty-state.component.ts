import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Uniform empty state — icon, heading, guidance text, optional action slot.
 * Never render a bare "no data" string; always explain why and what to do next.
 */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty" role="note">
      <span class="material-icons icon" aria-hidden="true">{{ icon() }}</span>
      <h3 class="heading">{{ heading() }}</h3>
      <p class="body"><ng-content /></p>
      <div class="actions"><ng-content select="[slot=actions]" /></div>
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: var(--ely-space-10) var(--ely-space-6);
      gap: var(--ely-space-2);
    }
    .icon {
      font-size: 36px;
      width: 36px;
      height: 36px;
      color: var(--ely-text-3);
      margin-bottom: var(--ely-space-2);
    }
    .heading {
      margin: 0;
      font: var(--ely-text-section);
      color: var(--ely-text);
    }
    .body {
      margin: 0;
      font: var(--ely-text-small);
      color: var(--ely-text-2);
      max-width: 380px;
    }
    .actions { margin-top: var(--ely-space-3); }
  `,
})
export class EmptyStateComponent {
  readonly heading = input.required<string>();
  readonly icon = input('inbox');
}
