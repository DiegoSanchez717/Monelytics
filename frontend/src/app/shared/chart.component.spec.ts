import { TestBed } from '@angular/core/testing';
import { ChartComponent } from './chart.component';

describe('accessible chart data summaries', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [ChartComponent] }));

  it('exposes the first and last labelled currency values in the image name', () => {
    const fixture = TestBed.createComponent(ChartComponent);
    fixture.componentRef.setInput('label', 'IRA balance over time');
    fixture.componentRef.setInput('values', [1000.1, 1100.35, 1250.75]);
    fixture.componentRef.setInput('labels', ['April', 'May', 'June']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toBe(
      'IRA balance over time. Start: April, $1,000.10. End: June, $1,250.75.',
    );
  });

  it('updates the summary when a new projection replaces the input data', () => {
    const fixture = TestBed.createComponent(ChartComponent);
    fixture.componentRef.setInput('label', 'Retirement projection');
    fixture.componentRef.setInput('values', [0, 1200]);
    fixture.componentRef.setInput('labels', ['Age 64', 'Age 65']);
    fixture.detectChanges();
    fixture.componentRef.setInput('values', [1000, 2200]);
    fixture.detectChanges();
    expect(fixture.componentInstance.summary()).toBe(
      'Retirement projection. Start: Age 64, $1,000.00. End: Age 65, $2,200.00.',
    );
  });

  it('describes a single point without implying a trend', () => {
    const fixture = TestBed.createComponent(ChartComponent);
    fixture.componentRef.setInput('values', [900]);
    fixture.componentRef.setInput('labels', ['June']);
    fixture.detectChanges();
    expect(fixture.componentInstance.summary()).toBe('Balance over time. June: $900.00.');
  });

  it('announces the absence of data instead of empty start and end values', () => {
    const fixture = TestBed.createComponent(ChartComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toBe(
      'Balance over time. No data available.',
    );
  });
});
