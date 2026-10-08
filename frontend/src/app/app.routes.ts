import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/auth.guard';
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing.component').then((m) => m.LandingComponent),
    title: 'Monelytics · A clearer path to retirement',
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth.component').then((m) => m.AuthComponent),
    title: 'Sign in · Monelytics',
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth.component').then((m) => m.AuthComponent),
    title: 'Create an account · Monelytics',
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard.component').then((m) => m.DashboardComponent),
    title: 'Overview · Monelytics',
  },
  {
    path: 'accounts',
    canActivate: [authGuard],
    loadComponent: () => import('./features/accounts.component').then((m) => m.AccountsComponent),
    title: 'IRA accounts · Monelytics',
  },
  {
    path: 'transactions',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/transactions.component').then((m) => m.TransactionsComponent),
    title: 'Transactions · Monelytics',
  },
  {
    path: 'goals',
    canActivate: [authGuard],
    loadComponent: () => import('./features/goals.component').then((m) => m.GoalsComponent),
    title: 'Retirement goals · Monelytics',
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/settings.component').then((m) => m.SettingsComponent),
    title: 'Settings · Monelytics',
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/admin.component').then((m) => m.AdminComponent),
    title: 'Audit log · Monelytics',
  },
  { path: '**', redirectTo: '' },
];
