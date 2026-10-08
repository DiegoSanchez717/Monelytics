import { Component, input, output } from '@angular/core';
import { IconComponent } from './icon.component';
@Component({
  selector: 'ml-state',
  imports: [IconComponent],
  templateUrl: './state.component.html',
})
export class StateComponent {
  readonly loading = input(false);
  readonly error = input('');
  readonly title = input('A fresh start');
  readonly description = input('Your activity will appear here.');
  readonly icon = input('leaf');
  readonly retry = output<void>();
}
