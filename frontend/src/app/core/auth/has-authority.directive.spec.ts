import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { HasAuthorityDirective } from './has-authority.directive';

@Component({
  imports: [HasAuthorityDirective],
  template: `
    <div id="single" *appHasAuthority="'alert:write'">allowed</div>
    <div id="any" *appHasAuthority="['tariff:read', 'carbon:read']">any-match</div>
    <div
      id="all"
      *appHasAuthority="['carbon:read', 'alert:write']; all: true"
    >
      all-match
    </div>
  `,
})
class HostComponent {}

class StubAuthService {
  readonly permissions = signal<ReadonlySet<string>>(
    new Set(['carbon:read', 'alert:write']),
  );
  hasAuthority(p: string): boolean {
    return this.permissions().has(p);
  }
  hasAnyAuthority(list: string[]): boolean {
    return list.length === 0 || list.some((p) => this.hasAuthority(p));
  }
}

describe('HasAuthorityDirective', () => {
  let stub: StubAuthService;

  beforeEach(async () => {
    stub = new StubAuthService();
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: AuthService, useValue: stub }],
    }).compileComponents();
  });

  it('renders content when the permission is granted', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('#single')).toBeTruthy();
    expect(el.querySelector('#any')).toBeTruthy();
  });

  it('enforces all-of when appHasAuthorityAll is set', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    // carbon:read + alert:write both granted → visible
    expect(el.querySelector('#all')).toBeTruthy();
  });

  it('hides content when permissions are missing', () => {
    stub.permissions.set(new Set(['carbon:read']));
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('#single')).toBeNull();
    // 'carbon:read' still satisfies the any-of list
    expect(el.querySelector('#any')).toBeTruthy();
    // 'alert:write' missing → all-of fails
    expect(el.querySelector('#all')).toBeNull();
  });
});
