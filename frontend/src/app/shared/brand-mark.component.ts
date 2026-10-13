import { Component } from '@angular/core';

@Component({
  selector: 'ml-brand-mark',
  template: `<svg viewBox="0 0 48 48" aria-hidden="true" focusable="false">
    <circle cx="24" cy="24" r="24" fill="#EF7A6C" />
    <path
      d="M12 34V14l12 15 12-15v20"
      fill="none"
      stroke="#132e35"
      stroke-width="5.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M12 34V14l12 15 12-15v20"
      fill="none"
      stroke="#48D1CC"
      stroke-width="3.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>`,
  styles: [
    ':host{display:inline-flex;width:100%;height:100%;flex-shrink:0}svg{display:block;width:100%;height:100%}',
  ],
})
export class BrandMarkComponent {}
