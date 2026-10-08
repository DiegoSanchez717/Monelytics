import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ApiService, errorMessage } from '../core/api.service';
import { AuditEntry, Page } from '../core/models';
import { IconComponent } from '../shared/icon.component';
import { StateComponent } from '../shared/state.component';
@Component({
  selector: 'wp-admin',
  imports: [DatePipe, IconComponent, StateComponent],
  templateUrl: './admin.component.html',
})
export class AdminComponent {
  private readonly api = inject(ApiService);
  readonly data = signal<Page<AuditEntry> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly page = signal(0);
  constructor() {
    void this.load();
  }
  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      this.data.set(await this.api.audit(this.page()));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
  changePage(direction: number): void {
    this.page.update((p) => p + direction);
    void this.load();
  }
}
