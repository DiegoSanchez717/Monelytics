package dev.wealthpath;

import static dev.wealthpath.ApiDtos.*;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
class ProjectionService {
  Calculation calculate(Calculate input) {
    if (input.retirementAge() <= input.currentAge())
      throw ApiException.invalid("Retirement age must be greater than your current age.");
    int years = input.retirementAge() - input.currentAge(), months = years * 12;
    double monthlyRate = input.annualReturn().doubleValue() / 1200.0;
    double savings = input.currentSavings().doubleValue(),
        payment = input.monthlyContribution().doubleValue();
    List<YearProjection> yearly = new ArrayList<>();
    yearly.add(new YearProjection(input.currentAge(), money(savings), input.currentSavings()));
    double balance = savings;
    // Contributions arrive at each month end. A zero-return path avoids division by zero in annuity
    // math.
    for (int month = 1; month <= months; month++) {
      balance = balance * (1 + monthlyRate) + payment;
      if (month % 12 == 0)
        yearly.add(
            new YearProjection(
                input.currentAge() + month / 12, money(balance), money(savings + payment * month)));
    }
    double factor = Math.pow(1 + monthlyRate, months);
    double annuity = monthlyRate == 0 ? months : (factor - 1) / monthlyRate;
    double needed = Math.max(0, (input.targetAmount().doubleValue() - savings * factor) / annuity);
    BigDecimal projected = money(balance), contributions = money(savings + payment * months);
    return new Calculation(
        projected,
        contributions,
        projected.subtract(contributions),
        input.targetAmount().subtract(projected).max(BigDecimal.ZERO),
        money(needed),
        yearly);
  }

  private BigDecimal money(double value) {
    if (!Double.isFinite(value))
      throw ApiException.invalid("The projection exceeds the supported range.");
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
