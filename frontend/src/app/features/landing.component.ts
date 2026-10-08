import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../shared/icon.component';
@Component({
  selector: 'ml-landing',
  imports: [RouterLink, IconComponent],
  templateUrl: './landing.component.html',
})
export class LandingComponent {}
