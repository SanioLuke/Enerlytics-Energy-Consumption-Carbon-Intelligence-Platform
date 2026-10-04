import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ContextService } from './context.service';

class StubAuthService {
  readonly activeOrganization = () => null;
}

function queryParamMap(params: Record<string, string>) {
  return {
    snapshot: {
      queryParamMap: {
        get: (key: string) => params[key] ?? null,
      },
    },
    queryParamMap: of(params),
  };
}

describe('ContextService', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  function setup(routeParams: Record<string, string>): ContextService {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useClass: StubAuthService },
        { provide: ActivatedRoute, useValue: queryParamMap(routeParams) },
      ],
    });
    // commit() writes query params via router.navigate — stub it so tests
    // don't depend on a real navigation round-trip against a stubbed route.
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    return TestBed.inject(ContextService);
  }

  it('restores scope and period from URL params', () => {
    const service = setup({ site: 'site-1', building: 'bldg-9', period: 'LAST_7_DAYS' });
    service.initFromRoute();

    expect(service.scope()).toEqual({ siteId: 'site-1', buildingId: 'bldg-9' });
    expect(service.period()).toBe('LAST_7_DAYS');
  });

  it('falls back to localStorage when the URL has no params', () => {
    localStorage.setItem(
      'ely.context',
      JSON.stringify({
        siteId: 'site-7',
        buildingId: null,
        period: 'YESTERDAY',
        granularity: 'DAY',
        compare: 'PRIOR_PERIOD',
      }),
    );
    const service = setup({});
    service.initFromRoute();

    expect(service.scope().siteId).toBe('site-7');
    expect(service.period()).toBe('YESTERDAY');
    expect(service.granularity()).toBe('DAY');
    expect(service.comparison()).toBe('PRIOR_PERIOD');
  });

  it('prefers URL params over stored state (shareable links win)', () => {
    localStorage.setItem(
      'ely.context',
      JSON.stringify({ siteId: 'site-stored', buildingId: null, period: 'TODAY', granularity: 'HOUR', compare: 'NONE' }),
    );
    const service = setup({ site: 'site-url' });
    service.initFromRoute();

    expect(service.scope().siteId).toBe('site-url');
  });

  it('rejects invalid param values and uses defaults', () => {
    const service = setup({ period: 'FOREVER', granularity: 'MINUTE' });
    service.initFromRoute();

    expect(service.period()).toBe('TODAY');
    expect(service.granularity()).toBe('HOUR');
  });

  it('clears the building when the site is cleared', () => {
    const service = setup({ site: 's', building: 'b' });
    service.initFromRoute();

    service.setScope(null);

    expect(service.scope()).toEqual({ siteId: null, buildingId: null });
  });

  it('omits default values from shareable query params', () => {
    const service = setup({});
    service.initFromRoute();
    service.setScope('site-3', 'bldg-1');

    const params = service.queryParams() as Record<string, unknown>;
    expect(params['site']).toBe('site-3');
    expect(params['building']).toBe('bldg-1');
    // defaults are null → dropped by the router
    expect(params['period']).toBeNull();
    expect(params['granularity']).toBeNull();
    expect(params['compare']).toBeNull();
  });
});
