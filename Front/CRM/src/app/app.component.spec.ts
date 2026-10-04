import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';

import { provideTestDependencies } from './testing/test-dependencies';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  let component: AppComponent;
  let fixture: ComponentFixture<AppComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideTestDependencies()],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the app', () => {
    expect(component).toBeTruthy();
  });

  it('should have the "CRM" title', () => {
    expect(component.title).toEqual('CRM');
  });

  /**
   * The generated spec asserted on a <h1>Hello, CRM</h1> that no longer exists
   * in the template, so it could never pass. It is replaced by checks on the
   * behaviour the component actually has.
   */
  describe('layout', () => {
    it('treats the root path as a fullscreen route', () => {
      // The root route is declared as '' in app.routes.ts, but Router.url
      // reports '/'. Missing '/' here made the login page render with the
      // header and the navbar.
      const router = TestBed.inject(Router);
      expect(router.url).toBe('/');
      expect(component.showChrome).toBeFalse();
    });

    it('hides the chrome on a fullscreen route', () => {
      expect(component.showChrome).toBeFalse();
      expect(fixture.debugElement.query(By.css('app-header'))).toBeNull();
      expect(fixture.debugElement.query(By.css('app-navbar'))).toBeNull();
    });

    it('always renders the router outlet', () => {
      expect(fixture.debugElement.query(By.css('router-outlet'))).not.toBeNull();
    });
  });
});