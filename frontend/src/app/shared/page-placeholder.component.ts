import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EmptyStateComponent } from '../core/ui/empty-state.component';

/**
 * Structural placeholder for routes whose business pages are not yet built.
 * Title/description/icon come from route data so one component serves every
 * stubbed feature.
 */
@Component({
  selector: 'app-page-placeholder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EmptyStateComponent],
  template: `
    <div class="page">
      <header class="page-header">
        <h1 class="title">{{ title }}</h1>
      </header>
      <div class="ely-card">
        <app-empty-state [heading]="title + ' is coming soon'" [icon]="icon">
          {{ description }}
        </app-empty-state>
      </div>
    </div>
  `,
  styles: `
    .page { display: flex; flex-direction: column; gap: var(--ely-space-6); }
    .title { margin: 0; font: var(--ely-text-title); }
  `,
})
export class PagePlaceholderComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly title = (this.route.snapshot.data['title'] as string) ?? 'Page';
  protected readonly description =
    (this.route.snapshot.data['description'] as string) ??
    'This surface is part of the Enerlytics roadmap and has not been implemented yet.';
  protected readonly icon = (this.route.snapshot.data['icon'] as string) ?? 'construction';
}
