import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { CategoryTotal } from '../core/models';
@Component({
  selector: 'ml-category-breakdown',
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './category-breakdown.component.html',
})
export class CategoryBreakdownComponent {
  readonly categories = input<CategoryTotal[]>([]);
  total(): number {
    return this.categories().reduce((sum, c) => sum + c.value, 0);
  }
  percentage(value: number): number {
    return this.total() > 0 ? (value / this.total()) * 100 : 0;
  }
}
