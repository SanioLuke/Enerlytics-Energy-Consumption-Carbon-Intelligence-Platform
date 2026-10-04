import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { type ApiError } from '../api/api-error';

/**
 * Uniform API error state. Surfaces the RFC 9457 detail and a copyable
 * correlation ID per UX_SPEC feedback rules. Emits retry when the caller
 * provides a handler.
 */
@Component({
  selector: 'app-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error" role="alert">
      <span class="material-icons icon" aria-hidden="true">error_outline</span>
      <h3 class="heading">{{ heading() }}</h3>
      <p class="detail">{{ detail() }}</p>
      @if (error()?.correlationId; as correlationId) {
        <p class="correlation">
          Reference: <code>{{ correlationId }}</code>
        </p>
      }
      @if (showRetry()) {
        <button type="button" class="retry" (click)="retry.emit()">
          <span class="material-icons" aria-hidden="true">refresh</span>
          Retry
        </button>
      }
    </div>
  `,
  styles: `
    .error {
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
      color: var(--ely-critical);
      margin-bottom: var(--ely-space-2);
    }
    .heading {
      margin: 0;
      font: var(--ely-text-section);
      color: var(--ely-text);
    }
    .detail {
      margin: 0;
      font: var(--ely-text-small);
      color: var(--ely-text-2);
      max-width: 420px;
    }
    .correlation {
      margin: 0;
      font: var(--ely-text-micro);
      color: var(--ely-text-3);
    }
    .correlation code {
      font-family: var(--ely-font-mono);
      font-size: 11px;
      background: var(--ely-bg-subtle);
      padding: 1px 5px;
      border-radius: var(--ely-radius-sm);
      user-select: all;
    }
    .retry {
      margin-top: var(--ely-space-3);
      display: inline-flex;
      align-items: center;
      gap: var(--ely-space-2);
      height: 36px;
      padding: 0 var(--ely-space-4);
      border: 1px solid var(--ely-border-strong);
      border-radius: var(--ely-radius-sm);
      background: var(--ely-surface);
      color: var(--ely-text);
      font: var(--ely-text-body);
      font-weight: 500;
      cursor: pointer;
    }
    .retry:hover { border-color: var(--ely-accent); color: var(--ely-accent); }
    .retry .material-icons { font-size: 16px; width: 16px; height: 16px; }
  `,
})
export class ErrorStateComponent {
  readonly error = input<ApiError | null>(null);
  readonly heading = input('Something went wrong');
  readonly showRetry = input(true);
  readonly retry = output<void>();

  protected readonly detail = computed(() => {
    const e = this.error();
    if (!e) {
      return 'An unexpected error occurred. Please try again.';
    }
    if (e.status === 0) {
      return 'Cannot reach the Enerlytics API. Check your connection and try again.';
    }
    return e.detail;
  });
}
