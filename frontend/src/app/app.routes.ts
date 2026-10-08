import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/auth.guard';
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing.component').then((m) => m.LandingComponent),
    title: 'WealthPath · A clearer path to retirement',
  },
  {
    path: 'login',
    loadComponent: () => import('./features/auth.component').then((m) => m.AuthComponent),
    title: 'Sign in · WealthPath',
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth.component').then((m) => m.AuthComponent),
    title: 'Create an account · WealthPath',
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard.component').then((m) => m.DashboardComponent),
    title: 'Overview · WealthPath',
  },
  {
    path: 'accounts',
    canActivate: [authGuard],
    loadComponent: () => import('./features/accounts.component').then((m) => m.AccountsComponent),
    title: 'IRA accounts · WealthPath',
  },
  {
    path: 'transactions',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/transactions.component').then((m) => m.TransactionsComponent),
    title: 'Transactions · WealthPath',
  },
  {
    path: 'goals',
    canActivate: [authGuard],
    loadComponent: () => import('./features/goals.component').then((m) => m.GoalsComponent),
    title: 'Retirement goals · WealthPath',
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/settings.component').then((m) => m.SettingsComponent),
    title: 'Settings · WealthPath',
  },
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/admin.component').then((m) => m.AdminComponent),
    title: 'Audit log · WealthPath',
  },
  { path: '**', redirectTo: '' },
];
