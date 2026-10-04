import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { ApiError } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Login — UX_SPEC §4.1: centered two-panel card, brand column on the left,
 * form on the right. Field validation is inline; submit failures surface the
 * RFC 9457 detail in the banner.
 */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <div class="login-page">
      <div class="login-card">
        <div class="brand-panel">
          <div class="brand-mark" aria-hidden="true">E</div>
          <h1 class="brand-name">Enerlytics</h1>
          <p class="brand-tagline">
            Energy consumption and carbon intelligence for every facility.
          </p>
        </div>

        <form class="form-panel" [formGroup]="form" (ngSubmit)="submit()">
          <h2 class="form-title">Sign in</h2>

          @if (submitError(); as error) {
            <div class="error-banner" role="alert">
              <span>{{ error }}</span>
            </div>
          }

          <div class="field">
            <label for="email">Email</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              autocomplete="username"
              [class.invalid]="emailInvalid()"
            />
            @if (emailInvalid()) {
              <span class="field-error">Enter a valid email address.</span>
            }
          </div>

          <div class="field">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              formControlName="password"
              autocomplete="current-password"
              [class.invalid]="passwordInvalid()"
            />
            @if (passwordInvalid()) {
              <span class="field-error">Password is required.</span>
            }
          </div>

          <button type="submit" class="submit" [disabled]="submitting()">
            @if (submitting()) {
              <span class="spinner" aria-hidden="true"></span>
            }
            Sign in
          </button>
        </form>
      </div>
    </div>
  `,
  styles: `
    .login-page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--ely-space-6);
      background: var(--ely-bg);
    }

    .login-card {
      display: grid;
      grid-template-columns: 300px 1fr;
      width: min(720px, 100%);
      background: var(--ely-surface);
      border: 1px solid var(--ely-border);
      border-radius: var(--ely-radius-lg);
      overflow: hidden;
    }

    .brand-panel {
      background: var(--ely-accent);
      color: #fff;
      padding: var(--ely-space-10) var(--ely-space-6);
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: var(--ely-space-3);
    }
    .brand-mark {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: var(--ely-radius-md);
      background: rgba(255, 255, 255, 0.18);
      font: var(--ely-text-title);
    }
    .brand-name { margin: 0; font: var(--ely-text-title); }
    .brand-tagline { margin: 0; font: var(--ely-text-small); opacity: 0.85; }

    .form-panel {
      padding: var(--ely-space-10) var(--ely-space-8);
      display: flex;
      flex-direction: column;
      gap: var(--ely-space-4);
    }
    .form-title { margin: 0 0 var(--ely-space-2); font: var(--ely-text-title); }

    .error-banner {
      padding: var(--ely-space-3) var(--ely-space-4);
      border: 1px solid color-mix(in srgb, var(--ely-critical) 35%, transparent);
      border-left: 3px solid var(--ely-critical);
      border-radius: var(--ely-radius-sm);
      background: color-mix(in srgb, var(--ely-critical) 6%, white);
      color: var(--ely-critical);
      font: var(--ely-text-small);
    }

    .field { display: flex; flex-direction: column; gap: var(--ely-space-1); }
    .field label {
      font: var(--ely-text-caption);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--ely-text-3);
    }
    .field input {
      height: 36px;
      padding: 0 var(--ely-space-3);
      border: 1px solid var(--ely-border-strong);
      border-radius: var(--ely-radius-sm);
      font: var(--ely-text-body);
      color: var(--ely-text);
      background: var(--ely-surface);
    }
    .field input:focus {
      outline: none;
      border-color: var(--ely-accent);
      box-shadow: 0 0 0 2px var(--ely-focus-ring);
    }
    .field input.invalid { border-color: var(--ely-critical); }
    .field-error { font: var(--ely-text-small); color: var(--ely-critical); }

    .submit {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--ely-space-2);
      height: 36px;
      margin-top: var(--ely-space-2);
      border: none;
      border-radius: var(--ely-radius-sm);
      background: var(--ely-accent);
      color: #fff;
      font: var(--ely-text-body);
      font-weight: 500;
      cursor: pointer;
    }
    .submit:hover:not(:disabled) { background: var(--ely-accent-strong); }
    .submit:disabled { opacity: 0.45; cursor: not-allowed; }

    .spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255, 255, 255, 0.4);
      border-top-color: #fff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    @media (max-width: 640px) {
      .login-card { grid-template-columns: 1fr; }
      .brand-panel { padding: var(--ely-space-6); }
      .form-panel { padding: var(--ely-space-6); }
    }
  `,
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.dirty || control.touched);
  }

  protected passwordInvalid(): boolean {
    const control = this.form.controls.password;
    return control.invalid && (control.dirty || control.touched);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);

    const { email, password } = this.form.getRawValue();
    this.auth
      .login(email, password)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          const returnUrl =
            (this.route.snapshot.queryParamMap.get('returnUrl') as string | null) ??
            '/overview';
          this.router.navigateByUrl(returnUrl);
        },
        error: (error: unknown) => {
          this.submitError.set(
            error instanceof ApiError
              ? error.status === 401
                ? 'Invalid email or password.'
                : error.detail
              : 'Sign-in failed. Please try again.',
          );
        },
      });
  }
}
