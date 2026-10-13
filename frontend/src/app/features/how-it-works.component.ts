import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'ml-how-it-works',
  imports: [RouterLink, IconComponent],
  templateUrl: './how-it-works.component.html',
})
export class HowItWorksComponent {
  readonly auth = inject(AuthService);
}
