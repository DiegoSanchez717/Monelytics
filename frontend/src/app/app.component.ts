import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { IconComponent } from './shared/icon.component';
import { errorMessage } from './core/api.service';
@Component({
  selector: 'wp-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  templateUrl: './app.component.html',
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly mobileOpen = signal(false);
  readonly logoutError = signal('');
  constructor() {
    void this.auth.session();
  }
  async logout(): Promise<void> {
    try {
      await this.auth.logout();
      await this.router.navigateByUrl('/login');
    } catch (error) {
      this.logoutError.set(errorMessage(error));
    }
  }
}
