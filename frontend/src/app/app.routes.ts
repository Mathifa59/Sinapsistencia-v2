import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guard';

/**
 * Ruteo por rol — replica el proxy.ts legacy:
 * /login (con redirect), /doctor/**, /lawyer/**, /admin/** protegidos por rol.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'doctor',
    canActivate: [authGuard, roleGuard('doctor')],
    loadComponent: () =>
      import('./shared/layout/portal-layout.component').then((m) => m.PortalLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/doctor/dashboard/dashboard.component').then((m) => m.DoctorDashboardComponent),
      },
      {
        path: 'cases',
        loadComponent: () =>
          import('./features/doctor/cases/cases-list.component').then((m) => m.DoctorCasesComponent),
      },
      {
        path: 'cases/:id',
        loadComponent: () =>
          import('./features/doctor/cases/case-detail.component').then((m) => m.DoctorCaseDetailComponent),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./features/doctor/documents/documents-list.component').then((m) => m.DoctorDocumentsComponent),
      },
      {
        path: 'lawyers',
        loadComponent: () =>
          import('./features/doctor/lawyers/lawyers.component').then((m) => m.DoctorLawyersComponent),
      },
      {
        path: 'risk',
        loadComponent: () =>
          import('./features/doctor/risk/risk.component').then((m) => m.DoctorRiskComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/doctor/profile/profile.component').then((m) => m.DoctorProfileComponent),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
  {
    path: 'lawyer',
    canActivate: [authGuard, roleGuard('lawyer')],
    loadComponent: () =>
      import('./shared/layout/portal-layout.component').then((m) => m.PortalLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/lawyer/dashboard/dashboard.component').then((m) => m.LawyerDashboardComponent),
      },
      {
        path: 'requests',
        loadComponent: () =>
          import('./features/lawyer/requests/requests.component').then((m) => m.LawyerRequestsComponent),
      },
      {
        path: 'doctors',
        loadComponent: () =>
          import('./features/lawyer/doctors/doctors.component').then((m) => m.LawyerDoctorsComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/lawyer/profile/profile.component').then((m) => m.LawyerProfileComponent),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard('admin')],
    loadComponent: () =>
      import('./shared/layout/portal-layout.component').then((m) => m.PortalLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/admin/dashboard/dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/admin/users/users.component').then((m) => m.AdminUsersComponent),
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./features/admin/documents/documents.component').then((m) => m.AdminDocumentsComponent),
      },
      {
        path: 'metrics',
        loadComponent: () =>
          import('./features/admin/metrics/metrics.component').then((m) => m.AdminMetricsComponent),
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/admin/audit/audit.component').then((m) => m.AdminAuditComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/admin/profile/profile.component').then((m) => m.AdminProfileComponent),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
