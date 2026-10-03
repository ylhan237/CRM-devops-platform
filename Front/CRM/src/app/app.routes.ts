import { Routes } from '@angular/router';
import { authGuard } from './guards/auth/auth.guard';

/**
 * Every route is loaded lazily via `loadComponent` so that its component, its
 * template and its private dependencies land in a separate chunk instead of the
 * initial bundle.
 *
 * The heavy libraries are isolated this way:
 *   - @syncfusion/ej2-angular-schedule -> calendar
 *   - jspdf + html2canvas              -> candidate-detail, update-candidate
 *   - xlsx                             -> task
 *   - chart.js                         -> dashboard, task
 */
export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: 'forget-password', loadComponent: () => import('./pages/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent) },
  { path: 'selfCreation', loadComponent: () => import('./pages/self-creationn/self-creationn.component').then(m => m.SelfCreationnComponent) },

  { path: 'dashboard', canActivate: [authGuard], loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'users', canActivate: [authGuard], loadComponent: () => import('./pages/user/user.component').then(m => m.UserComponent) },
  { path: 'users/:id', canActivate: [authGuard], loadComponent: () => import('./components/userdetails/userdetails.component').then(m => m.UserdetailsComponent) },
  { path: 'pipeline', canActivate: [authGuard], loadComponent: () => import('./pages/pipeline/pipeline.component').then(m => m.PipelineComponent) },
  { path: 'candidates', canActivate: [authGuard], loadComponent: () => import('./pages/candidates/candidates.component').then(m => m.CandidatesComponent) },
  { path: 'candidates/new', canActivate: [authGuard], loadComponent: () => import('./components/candidate-form/candidate-form.component').then(m => m.CandidateFormComponent) },
  { path: 'calendar', canActivate: [authGuard], loadComponent: () => import('./pages/calender/calender.component').then(m => m.CalenderComponent) },
  { path: 'event', canActivate: [authGuard], loadComponent: () => import('./pages/event/event.component').then(m => m.EventComponent) },
  { path: 'venue', canActivate: [authGuard], loadComponent: () => import('./pages/venue/venue.component').then(m => m.VenueComponent) },
  { path: 'type', canActivate: [authGuard], loadComponent: () => import('./pages/type/type.component').then(m => m.TypeComponent) },
  { path: 'task', canActivate: [authGuard], loadComponent: () => import('./pages/task/task.component').then(m => m.TaskComponent) },
  { path: 'contact', canActivate: [authGuard], loadComponent: () => import('./pages/contact/contact.component').then(m => m.ContactComponent) },
  { path: 'candidate/:id', canActivate: [authGuard], loadComponent: () => import('./pages/candidate-detail/candidate-detail.component').then(m => m.CandidateDetailComponent) },
  { path: 'candidate-detail', canActivate: [authGuard], loadComponent: () => import('./pages/candidate-detail/candidate-detail.component').then(m => m.CandidateDetailComponent) },
  { path: 'update-candidate/:id', canActivate: [authGuard], loadComponent: () => import('./pages/update-candidate/update-candidate.component').then(m => m.UpdateCandidateComponent) },

  { path: '**', redirectTo: '' }
];
