package dev.monelytics;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityContextRepository securityContexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  DefaultCookieSerializer sessionCookies(@Value("${monelytics.cookie-secure}") boolean secure) {
    // Explicitly apply the shared JDBC session policy in both servlet and test environments.
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("MONELYTICS_SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(secure);
    serializer.setSameSite("Lax");
    return serializer;
  }

  @Bean
  CookieCsrfTokenRepository csrfRepository(@Value("${monelytics.cookie-secure}") boolean secure) {
    CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repo.setCookieCustomizer(cookie -> cookie.path("/").secure(secure).sameSite("Lax"));
    return repo;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      SecurityContextRepository contexts,
      CookieCsrfTokenRepository csrf,
      ObjectMapper mapper)
      throws Exception {
    // Angular sends the plain cookie token; the raw handler avoids Spring's XOR HTML token
    // encoding.
    http.csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/audit/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            c ->
                c.authenticationEntryPoint(
                        (req, res, e) ->
                            securityProblem(
                                mapper,
                                req,
                                res,
                                401,
                                "AUTHENTICATION_REQUIRED",
                                "Sign in to continue."))
                    .accessDeniedHandler(
                        (req, res, e) ->
                            securityProblem(
                                mapper,
                                req,
                                res,
                                403,
                                "ACCESS_DENIED",
                                "You do not have permission or your security token expired.")))
        .headers(
            c ->
                c.contentSecurityPolicy(
                        p ->
                            p.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(
                        p ->
                            p.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .permissionsPolicyHeader(
                        p -> p.policy("camera=(), microphone=(), geolocation=()")))
        .logout(c -> c.disable())
        .addFilterBefore(new RequestContextFilter(mapper), SecurityContextHolderFilter.class);
    return http.build();
  }

  static void securityProblem(
      ObjectMapper mapper,
      HttpServletRequest req,
      HttpServletResponse res,
      int status,
      String code,
      String detail)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(
        res.getOutputStream(),
        Map.of(
            "status",
            status,
            "title",
            HttpStatus.valueOf(status).getReasonPhrase(),
            "detail",
            detail,
            "code",
            code,
            "requestId",
            Objects.toString(req.getAttribute("requestId"), "")));
  }
}

class RequestContextFilter extends OncePerRequestFilter {
  static final int MAX_JSON_BYTES = 256 * 1024;

  private record Window(long started, int count) {}

  private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
  private final ObjectMapper mapper;

  RequestContextFilter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Request-Id");
    String id =
        supplied != null && supplied.matches("[a-zA-Z0-9-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    request.setAttribute("requestId", id);
    response.setHeader("X-Request-Id", id);
    MDC.put("requestId", id);
    try {
      if (request.getMethod().equals("POST")
          && (request.getRequestURI().equals("/api/auth/login")
              || request.getRequestURI().equals("/api/auth/register")
              || request.getRequestURI().startsWith("/api/auth/mfa/"))) {
        long now = System.currentTimeMillis();
        // Bounded, per-instance protection complements durable account lockout; deploy an edge rate
        // limit when scaling.
        if (attempts.size() > 10000)
          attempts.entrySet().removeIf(e -> now - e.getValue().started > 900000);
        if (attempts.size() > 10000) {
          SecurityConfiguration.securityProblem(
              mapper, request, response, 429, "RATE_LIMITED", "Try again later.");
          return;
        }
        Window w =
            attempts.compute(
                request.getRemoteAddr(),
                (key, old) ->
                    old == null || now - old.started > 900000
                        ? new Window(now, 1)
                        : new Window(old.started, old.count + 1));
        if (w.count > 30) {
          response.setHeader("Retry-After", "900");
          SecurityConfiguration.securityProblem(
              mapper,
              request,
              response,
              429,
              "RATE_LIMITED",
              "Too many sign-in attempts. Try again in 15 minutes.");
          return;
        }
      }
      if (request.getContentLengthLong() > MAX_JSON_BYTES) {
        rejectOversized(request, response);
        return;
      }
      String contentType = request.getContentType();
      String mediaType =
          contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
      if (mediaType.equals("application/json") || mediaType.endsWith("+json")) {
        // Read at most the cap plus one byte, including requests without Content-Length.
        // Reject before deserialization or business logic and replay the bounded body for MVC.
        byte[] body = request.getInputStream().readNBytes(MAX_JSON_BYTES + 1);
        if (body.length > MAX_JSON_BYTES) {
          rejectOversized(request, response);
          return;
        }
        request = new BoundedJsonRequest(request, body);
      }
      chain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
    }
  }

  private void rejectOversized(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    SecurityConfiguration.securityProblem(
        mapper,
        request,
        response,
        413,
        "PAYLOAD_TOO_LARGE",
        "JSON request bodies must not exceed 256 KiB.");
  }
}
