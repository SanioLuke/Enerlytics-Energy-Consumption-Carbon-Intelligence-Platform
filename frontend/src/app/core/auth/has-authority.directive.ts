import {
  Directive,
  TemplateRef,
  ViewContainerRef,
  effect,
  inject,
  input,
} from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Structural RBAC directive. Renders the embedded view only when the user
 * holds the required permission(s) in the active organization.
 *
 *   <button *appHasAuthority="'alert:write'">Acknowledge</button>
 *   <button *appHasAuthority="['tariff:read', 'tariff:write']">Edit</button>
 *
 * Pass `appHasAuthorityAll` to require every permission instead of any-of.
 */
@Directive({ selector: '[appHasAuthority]' })
export class HasAuthorityDirective {
  readonly appHasAuthority = input.required<string | string[]>();
  readonly appHasAuthorityAll = input(false);

  private readonly template = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly auth = inject(AuthService);

  constructor() {
    effect(() => {
      const required = this.normalize(this.appHasAuthority());
      const granted = this.appHasAuthorityAll()
        ? required.every((p) => this.auth.hasAuthority(p))
        : this.auth.hasAnyAuthority(required);

      this.viewContainer.clear();
      if (granted) {
        this.viewContainer.createEmbeddedView(this.template);
      }
    });
  }

  private normalize(value: string | string[]): string[] {
    return Array.isArray(value) ? value : [value];
  }
}
