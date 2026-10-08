package dev.wealthpath;

import static dev.wealthpath.ApiDtos.*;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class AuthController {
  private final AuthService auth;
  private final SecurityContextRepository contexts;
  private final CookieCsrfTokenRepository csrf;
  private final AuditService audit;

  AuthController(
      AuthService auth,
      SecurityContextRepository contexts,
      CookieCsrfTokenRepository csrf,
      AuditService audit) {
    this.auth = auth;
    this.contexts = contexts;
    this.csrf = csrf;
    this.audit = audit;
  }

  @GetMapping("/auth/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  UserView register(
      @Valid @RequestBody Register input, HttpServletRequest req, HttpServletResponse res) {
    return establish(auth.register(input), req, res);
  }

  @PostMapping("/auth/login")
  UserView login(@Valid @RequestBody Login input, HttpServletRequest req, HttpServletResponse res) {
    return establish(auth.login(input), req, res);
  }

  @GetMapping("/auth/me")
  UserView me(Authentication principal) {
    return UserView.of(auth.user(id(principal)));
  }

  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(Authentication principal, HttpServletRequest req, HttpServletResponse res) {
    UUID id = id(principal);
    audit.record(id, "USER_LOGOUT", id, "Signed out");
    new SecurityContextLogoutHandler().logout(req, res, principal);
    csrf.saveToken(null, req, res);
  }

  @PutMapping("/settings")
  UserView settings(Authentication principal, @Valid @RequestBody Settings input) {
    return auth.settings(id(principal), input);
  }

  @PostMapping("/auth/mfa/setup")
  MfaSetup setup(Authentication principal, @Valid @RequestBody Password input) {
    return auth.setup(id(principal), input);
  }

  @PostMapping("/auth/mfa/enable")
  UserView enable(Authentication principal, @Valid @RequestBody MfaConfirm input) {
    return auth.toggle(id(principal), input, true);
  }

  @PostMapping("/auth/mfa/disable")
  UserView disable(Authentication principal, @Valid @RequestBody MfaConfirm input) {
    return auth.toggle(id(principal), input, false);
  }

  private UserView establish(AppUser user, HttpServletRequest req, HttpServletResponse res) {
    // Rotate any anonymous session to prevent fixation, then persist authentication to the shared
    // JDBC store.
    if (req.getSession(false) != null) req.changeSessionId();
    var principal =
        new UsernamePasswordAuthenticationToken(
            user.id.toString(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name())));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(principal);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    csrf.saveToken(null, req, res);
    return UserView.of(user);
  }

  static UUID id(Authentication principal) {
    return UUID.fromString(principal.getName());
  }
}
