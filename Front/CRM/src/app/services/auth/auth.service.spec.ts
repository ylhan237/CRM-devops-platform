import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { provideTestDependencies } from '../../testing/test-dependencies';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideTestDependencies()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    http.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('logout', () => {
    const logoutUrl = `${environment.apiBaseUrl}/api/v1/auth/logout`;

    it('removes the token from storage', () => {
      service.saveToken('a-token');

      service.logout();

      http.expectOne(logoutUrl).flush({});
      expect(localStorage.getItem('jwtToken')).toBeNull();
    });

    /**
     * POST /auth/logout reads the token from the Authorization header. It used
     * to receive { token } in the body and answered 401 "Authorization token is
     * required", which the caller swallowed, so the token was never expired
     * server side.
     */
    it('sends the token in the Authorization header', () => {
      service.saveToken('a-token');

      service.logout();

      const request = http.expectOne(logoutUrl);
      expect(request.request.method).toBe('POST');
      expect(request.request.headers.get('Authorization')).toBe('Bearer a-token');
      expect(request.request.body).toBeNull();
      request.flush({});
    });

    it('navigates to the login page', () => {
      service.saveToken('a-token');

      service.logout();
      http.expectOne(logoutUrl).flush({});

      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('navigates to the login page even when the backend rejects the call', () => {
      service.saveToken('a-token');

      service.logout();
      http.expectOne(logoutUrl).flush('nope', { status: 500, statusText: 'Server Error' });

      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('navigates without calling the backend when there is no token', () => {
      service.logout();

      http.expectNone(logoutUrl);
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });
});
