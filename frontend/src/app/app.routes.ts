import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/auth.guard';
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/landing.component').then((m) => m.LandingComponent),
    title: 'Monelytics · A clearer picture of your money',
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/password-reset.component').then((m) => m.PasswordResetComponent),
    data: { mode: 'request' },
    title: 'Reset your password · Monelytics',
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/password-reset.component').then((m) => m.PasswordResetComponent),
    data: { mode: 'confirm' },
    title: 'Choose a new password · Monelytics',
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
    title: 'Accounts · Monelytics',
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
    title: 'Savings goals · Monelytics',
  },
  {
    path: 'budgets',
    canActivate: [authGuard],
    loadComponent: () => import('./features/budgets.component').then((m) => m.BudgetsComponent),
    title: 'Budgets · Monelytics',
  },
  {
    path: 'bills',
    canActivate: [authGuard],
    loadComponent: () => import('./features/bills.component').then((m) => m.BillsComponent),
    title: 'Bills & subscriptions · Monelytics',
  },
  {
    path: 'categories',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/categories.component').then((m) => m.CategoriesComponent),
    title: 'Categories · Monelytics',
  },
  {
    path: 'reports',
    canActivate: [authGuard],
    loadComponent: () => import('./features/reports.component').then((m) => m.ReportsComponent),
    title: 'Reports · Monelytics',
  },
  {
    path: 'assistant',
    canActivate: [authGuard],
    loadComponent: () => import('./features/assistant.component').then((m) => m.AssistantComponent),
    title: 'Financial assistant · Monelytics',
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
