package dev.wealthpath;

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
                .title("WealthPath API")
                .version("1.0.0")
                .description(
                    "Retirement portfolio tracking. Authenticate with /api/auth/login. Retrieve /api/auth/csrf before any mutation and refresh it after login/logout. Monetary values are USD. Projections and configurable contribution policies are educational assumptions."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "session",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("WEALTHPATH_SESSION"))
                .addSecuritySchemes(
                    "csrf",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-XSRF-TOKEN")))
        .addSecurityItem(new SecurityRequirement().addList("session").addList("csrf"));
  }
}
