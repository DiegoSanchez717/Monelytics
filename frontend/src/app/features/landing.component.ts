import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../shared/icon.component';
import { BrandMarkComponent } from '../shared/brand-mark.component';
import { AuthService } from '../core/auth.service';
@Component({
  selector: 'ml-landing',
  imports: [RouterLink, IconComponent, BrandMarkComponent],
  templateUrl: './landing.component.html',
})
export class LandingComponent {
  readonly auth = inject(AuthService);
}
