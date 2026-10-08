import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { GoalsComponent, retirementAgeValidator } from './goals.component';
import { passwordByteLimit } from '../core/validators';
describe('retirement planning forms', () => {
  const api = { goals: vi.fn().mockResolvedValue([]), calculate: vi.fn() };
  beforeEach(() => {
    api.calculate.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: ApiService, useValue: api }],
    });
  });
  it('rejects a retirement age before the current age', () => {
    const group = new FormGroup({
      currentAge: new FormControl(45),
      retirementAge: new FormControl(40),
    });
    expect(retirementAgeValidator(group)).toEqual({ ageOrder: true });
  });
  it('rejects the same current and retirement age', () => {
    const group = new FormGroup({
      currentAge: new FormControl(45),
      retirementAge: new FormControl(45),
    });
    expect(retirementAgeValidator(group)).toEqual({ ageOrder: true });
  });
  it('does not request a projection for invalid ages', async () => {
    const component = TestBed.runInInjectionContext(() => new GoalsComponent());
    component.calculator.patchValue({ currentAge: 60, retirementAge: 55 });
    await component.calculate();
    expect(api.calculate).not.toHaveBeenCalled();
    expect(component.calculator.touched).toBe(true);
  });
  it('sends a complete scenario and renders the returned projection', async () => {
    const projection = {
      projectedBalance: 500000,
      totalContributions: 200000,
      investmentGrowth: 300000,
      gap: 500000,
      monthlyNeeded: 800,
      yearlyProjection: [
        { age: 30, balance: 1000, contributions: 1000 },
        { age: 65, balance: 500000, contributions: 200000 },
      ],
    };
    api.calculate.mockResolvedValue(projection);
    const component = TestBed.runInInjectionContext(() => new GoalsComponent());
    await component.calculate();
    expect(api.calculate).toHaveBeenCalledWith({
      currentAge: 30,
      retirementAge: 65,
      currentSavings: 0,
      monthlyContribution: 500,
      annualReturn: 6,
      targetAmount: 1000000,
    });
    expect(component.projectionValues()).toEqual([1000, 500000]);
    expect(component.calculating()).toBe(false);
  });
  it('respects BCrypt’s byte limit for multi-byte passwords', () => {
    expect(passwordByteLimit(new FormControl('🔐'.repeat(19)))).toEqual({
      passwordBytes: true,
    });
    expect(passwordByteLimit(new FormControl('SecurePath!2026'))).toBeNull();
  });
});
