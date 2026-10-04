import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BreadcrumbService } from './breadcrumb.service';

/**
 * Breadcrumb trail driven by route `data['breadcrumb']` labels. The final
 * item is the current page and is not a link.
 */
@Component({
  selector: 'app-breadcrumb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    @if (crumbs().length > 1) {
      <nav class="breadcrumbs" aria-label="Breadcrumb">
        <ol>
          @for (crumb of crumbs(); track crumb.url; let last = $last) {
            <li>
              @if (last) {
                <span aria-current="page">{{ crumb.label }}</span>
              } @else {
                <a [routerLink]="crumb.url">{{ crumb.label }}</a>
                <span class="separator" aria-hidden="true">/</span>
              }
            </li>
          }
        </ol>
      </nav>
    }
  `,
  styles: `
    .breadcrumbs ol {
      display: flex;
      align-items: center;
      gap: var(--ely-space-2);
      list-style: none;
      margin: 0;
      padding: 0;
      font: var(--ely-text-small);
    }
    a {
      color: var(--ely-text-3);
      text-decoration: none;
    }
    a:hover { color: var(--ely-accent); }
    [aria-current='page'] { color: var(--ely-text); font-weight: 500; }
    .separator { color: var(--ely-text-3); margin-left: var(--ely-space-2); }
  `,
})
export class BreadcrumbComponent {
  private readonly service = inject(BreadcrumbService);
  protected readonly crumbs = this.service.items;
}
