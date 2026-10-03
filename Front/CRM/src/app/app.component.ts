import { Component, OnInit } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { HeaderComponent } from './components/header/header.component';
import { filter } from 'rxjs/operators';
import { AuthService } from './services/auth/auth.service';

/**
 * Routes rendered without the application chrome (no header, no navbar).
 *
 * The previous implementation compared mangled class names such as
 * "_LoginComponent". Those only exist in a production build, so the layout was
 * wrong under `ng serve`, and the names are no longer reachable now that every
 * route is lazy: `route.component` is undefined for a `loadComponent` route.
 * Matching on the URL is stable in both cases.
 */
const FULLSCREEN_ROUTES: readonly string[] = ['', '/login', '/forget-password', '/selfCreation'];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, HeaderComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {

  showChrome = true;

  constructor(private router: Router, private authService: AuthService) {}

  ngOnInit() {
    this.showChrome = !this.isFullscreenRoute(this.router.url);

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.showChrome = !this.isFullscreenRoute(this.router.url);
    });
  }

  private isFullscreenRoute(url: string): boolean {
    const path = url.split('?')[0].split('#')[0];
    return FULLSCREEN_ROUTES.includes(path);
  }

  title = 'CRM';
}
