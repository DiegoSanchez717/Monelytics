package dev.wealthpath;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
class ValidationConfiguration {
  @Bean
  LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    // Date validation and ledger reporting share UTC, including around local midnight boundaries.
    validator.setConfigurationInitializer(config -> config.clockProvider(Clock::systemUTC));
    return validator;
  }
}
