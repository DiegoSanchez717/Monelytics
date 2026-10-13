import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from './core/auth.service';
import { IconComponent } from './shared/icon.component';
import { errorMessage } from './core/api.service';
import { NotificationsComponent } from './shared/notifications.component';
import { BrandMarkComponent } from './shared/brand-mark.component';
@Component({
  selector: 'ml-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    IconComponent,
    NotificationsComponent,
    BrandMarkComponent,
  ],
  templateUrl: './app.component.html',
})
export class AppComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly mobileOpen = signal(false);
  readonly moreOpen = signal(false);
  readonly logoutError = signal('');
  constructor() {
    void this.auth.session();
    this.router.events.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event instanceof NavigationEnd) this.closeNavigation();
    });
  }
  closeNavigation(): void {
    this.mobileOpen.set(false);
    this.moreOpen.set(false);
  }
  @HostListener('document:keydown.escape') closeWithEscape(): void {
    const mobile = this.mobileOpen();
    const more = this.moreOpen();
    this.closeNavigation();
    if (mobile) this.element.nativeElement.querySelector<HTMLElement>('.mobile-toggle')?.focus();
    else if (more)
      this.element.nativeElement.querySelector<HTMLElement>('.nav-more > summary')?.focus();
  }
  @HostListener('document:click', ['$event']) closeOutside(event: MouseEvent): void {
    const target = event.target as Element;
    if (!this.element.nativeElement.querySelector('.topbar')?.contains(target))
      this.closeNavigation();
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
