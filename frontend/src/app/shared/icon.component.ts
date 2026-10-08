import { Component, input } from '@angular/core';
const paths: Record<string, string> = {
  home: 'M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z',
  wallet: 'M3 6a2 2 0 0 1 2-2h14v4M3 6v13a2 2 0 0 0 2 2h15V8H5a2 2 0 0 1-2-2zm13 7h4v4h-4z',
  arrows: 'M3 7h17m-4-4 4 4-4 4M21 17H4m4-4-4 4 4 4',
  target: 'M21 12a9 9 0 1 1-9-9m0 4a5 5 0 1 0 5 5m-5 0 9-9m-5 0h5v5',
  settings:
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zm-2-5h4l1 3 3 1 3 3v4l-3 3-3 1-1 3h-4l-1-3-3-1-3-3v-4l3-3 3-1z',
  shield: 'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6zm-4 9 3 3 5-6',
  plus: 'M12 5v14M5 12h14',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  up: 'M4 16l6-6 4 4 6-9m-6 0h6v6',
  check: 'M5 12l4 4L19 6',
  close: 'M6 6l12 12M6 18 18 6',
  search: 'M21 21l-5-5m2-6a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  leaf: 'M5 19c-5-9 6-15 15-15 0 9-5 20-15 15zm0 0L15 9',
  clock: 'M12 8v5l3 2m6-3a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  person: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21v-2a8 8 0 0 1 16 0v2',
  logout: 'M9 4H4v16h5m3-8h9m-4-4 4 4-4 4',
  edit: 'M14 5l5 5M4 20l4-1L21 6l-4-4L4 15z',
  trash: 'M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7',
  download: 'M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5',
  menu: 'M4 6h16M4 12h16M4 18h16',
  chevron: 'M6 9l6 6 6-6',
  mail: 'M3 5h18v14H3zm0 0 9 8 9-8',
  lock: 'M6 10h12v11H6zm2 0V7a4 4 0 0 1 8 0v3',
  info: 'M12 11v5m0-9v.1m9 4.9a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  budget: 'M3 5h18v14H3zm4 4h5m-5 4h3m5-4h2m-2 4h2',
  bill: 'M6 3h12v18l-3-2-3 2-3-2-3 2zm3 5h6m-6 4h6',
  chart: 'M4 3v18h17M8 17v-5m5 5V8m5 9V5',
  bell: 'M18 8a6 6 0 0 0-12 0v5l-2 3h16l-2-3zm-8 12h4',
  sparkles: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5zm7-1v4m-2-2h4',
  card: 'M3 5h18v14H3zm0 4h18M7 15h4',
  chat: 'M4 4h16v12H9l-5 4zm4 5h8m-8 3h5',
};
@Component({
  selector: 'ml-icon',
  template:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path()"/></svg>',
  styles: [
    ':host{display:inline-flex;width:21px;height:21px;flex-shrink:0}svg{width:100%;height:100%}',
  ],
})
export class IconComponent {
  readonly name = input('home');
  path(): string {
    return paths[this.name()] ?? paths['home'];
  }
}
