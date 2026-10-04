import { Injectable, inject, signal } from '@angular/core';
import { type ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

export interface BreadcrumbItem {
  label: string;
  url: string;
}

/**
 * Builds breadcrumbs by walking the activated route tree and collecting
 * `data['breadcrumb']` labels. A string value is static; a function receives
 * the route snapshot for param-aware labels (e.g. site names resolved
 * upstream).
 */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);
  readonly items = signal<BreadcrumbItem[]>([]);

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.rebuild());
  }

  private rebuild(): void {
    const items: BreadcrumbItem[] = [];
    let node = this.router.routerState.snapshot.root;
    let url = '';

    while (node.firstChild) {
      node = node.firstChild;
      url += this.segmentPath(node);
      const crumb = node.data['breadcrumb'] as
        | string
        | ((route: ActivatedRouteSnapshot) => string)
        | undefined;
      if (crumb) {
        items.push({
          label: typeof crumb === 'function' ? crumb(node) : crumb,
          url,
        });
      }
    }
    this.items.set(items);
  }

  private segmentPath(node: ActivatedRouteSnapshot): string {
    const segments = node.url.map((s) => s.path).join('/');
    return segments ? `/${segments}` : '';
  }
}
