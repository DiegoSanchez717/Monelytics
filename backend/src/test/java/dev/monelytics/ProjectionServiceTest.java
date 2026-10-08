package dev.monelytics;

import static dev.monelytics.ApiDtos.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProjectionServiceTest {
  private final ProjectionService projections = new ProjectionService();

  @Test
  void zeroReturnUsesMonthlyContributionsWithoutDivideByZero() {
    Calculation result =
        projections.calculate(
            new Calculate(
                30,
                60,
                new BigDecimal("10000"),
                new BigDecimal("500"),
                BigDecimal.ZERO,
                new BigDecimal("500000")));
    assertThat(result.projectedBalance()).isEqualByComparingTo("190000.00");
    assertThat(result.investmentGrowth()).isEqualByComparingTo("0");
    assertThat(result.monthlyNeeded()).isEqualByComparingTo("1361.11");
    assertThat(result.yearlyProjection()).hasSize(31);
  }

  @Test
  void compoundReturnMatchesAnnuityFormula() {
    Calculation result =
        projections.calculate(
            new Calculate(
                30,
                40,
                new BigDecimal("10000"),
                new BigDecimal("500"),
                new BigDecimal("6"),
                new BigDecimal("100000")));
    assertThat(result.projectedBalance()).isEqualByComparingTo("100133.64");
    assertThat(result.totalContributions()).isEqualByComparingTo("70000.00");
    assertThat(result.gap()).isEqualByComparingTo("0");
  }

  @Test
  void retirementMustFollowCurrentAge() {
    assertThatThrownBy(
            () ->
                projections.calculate(
                    new Calculate(
                        60, 60, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE)))
        .isInstanceOf(ApiException.class);
  }
}
