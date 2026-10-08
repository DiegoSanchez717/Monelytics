package dev.monelytics;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
class OpenApiConfiguration {
  @Bean
  OpenAPI apiDocumentation() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Monelytics API")
                .version("1.0.0")
                .description(
                    "Personal finance accounts, transactions, budgets, bills, savings, and a read-only educational assistant. Authenticate with /api/auth/login. Retrieve /api/auth/csrf before mutations and refresh after login/logout. Monetary values are USD; assessments are educational, not professional financial advice."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "session",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("MONELYTICS_SESSION"))
                .addSecuritySchemes(
                    "csrf",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-XSRF-TOKEN")))
        .addSecurityItem(new SecurityRequirement().addList("session").addList("csrf"));
  }
}
