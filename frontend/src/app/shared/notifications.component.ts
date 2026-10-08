import { Component, ElementRef, HostListener, ViewChild, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { currentMonth } from '../core/finance.utils';
import { FinanceNotification } from '../core/models';
import { IconComponent } from './icon.component';
@Component({
  selector: 'ml-notifications',
  imports: [RouterLink, IconComponent],
  templateUrl: './notifications.component.html',
})
export class NotificationsComponent {
  private readonly api = inject(ApiService);
  private readonly element = inject(ElementRef<HTMLElement>);
  readonly open = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly items = signal<FinanceNotification[]>([]);
  readonly dismissed = signal<Set<string>>(new Set());
  @ViewChild('toggle') toggle?: ElementRef<HTMLButtonElement>;
  constructor() {
    void this.load();
  }
  visible(): FinanceNotification[] {
    return this.items().filter((i) => !this.dismissed().has(i.id));
  }
  togglePanel(): void {
    this.open.update((open) => !open);
    if (this.open()) void this.load();
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.items.set(await this.api.notifications(currentMonth()));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  dismiss(id: string): void {
    this.dismissed.update((ids) => new Set([...ids, id]));
  }
  route(item: FinanceNotification): string {
    return {
      BUDGET: '/budgets',
      BILL: '/bills',
      UNUSUAL_SPENDING: '/transactions',
      GOAL_MILESTONE: '/goals',
    }[item.type];
  }
  @HostListener('document:click', ['$event']) outside(event: MouseEvent): void {
    if (!this.element.nativeElement.contains(event.target as Node)) this.open.set(false);
  }
  @HostListener('document:keydown.escape') escape(): void {
    if (this.open()) {
      this.open.set(false);
      this.toggle?.nativeElement.focus();
    }
  }
}
