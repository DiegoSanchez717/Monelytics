import { Component, computed, input } from '@angular/core';
@Component({
  selector: 'ml-chart',
  templateUrl: './chart.component.html',
  styleUrl: './chart.component.css',
})
export class ChartComponent {
  readonly values = input<number[]>([]);
  readonly labels = input<string[]>([]);
  readonly label = input('Balance over time');
  readonly gradientId = `chart-${crypto.randomUUID()}`;
  private readonly currency = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  });
  // A chart's name alone does not communicate its data to a screen reader.
  readonly summary = computed(() => {
    const title = this.label().replace(/[.!?]+$/, '');
    const values = this.values();
    const labels = this.labels();
    if (!values.length) return `${title}. No data available.`;
    if (values.length === 1) {
      return `${title}. ${labels[0] ? `${labels[0]}: ` : 'Balance: '}${this.currency.format(values[0])}.`;
    }
    const first = `${labels[0] ? `${labels[0]}, ` : ''}${this.currency.format(values[0])}`;
    const lastLabel = labels.at(-1);
    const last = `${lastLabel ? `${lastLabel}, ` : ''}${this.currency.format(values.at(-1)!)}`;
    return `${title}. Start: ${first}. End: ${last}.`;
  });
  readonly points = computed(() => {
    const values = this.values();
    const minimum = Math.min(...values, 0);
    const maximum = Math.max(...values, 1);
    return values.map((value, index) => ({
      x: values.length === 1 ? 450 : (index / (values.length - 1)) * 900,
      y: 205 - ((value - minimum) / (maximum - minimum)) * 180,
    }));
  });
  readonly line = computed(() =>
    this.points()
      .map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(2)} ${p.y.toFixed(2)}`)
      .join(' '),
  );
  readonly area = computed(() => `${this.line()} L900 220 L0 220 Z`);
}
