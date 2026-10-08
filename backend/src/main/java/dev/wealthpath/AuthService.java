package dev.wealthpath;

import static dev.wealthpath.ApiDtos.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final TotpService totp;
  private final AuditService audit;
  private final String dummyHash;

  AuthService(
      UserRepository users, PasswordEncoder passwords, TotpService totp, AuditService audit) {
    this.users = users;
    this.passwords = passwords;
    this.totp = totp;
    this.audit = audit;
    dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  @Transactional
  AppUser register(Register input) {
    checkPasswordBytes(input.password());
    String email = normalizeEmail(input.email());
    if (users.existsByEmail(email))
      throw new ApiException(
          HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account already exists with that email.");
    AppUser user = new AppUser();
    user.firstName = input.firstName().trim();
    user.lastName = input.lastName().trim();
    user.email = email;
    user.passwordHash = passwords.encode(input.password());
    users.saveAndFlush(user);
    audit.record(user.id, "USER_REGISTERED", user.id, "Account registered");
    return user;
  }

  @Transactional(noRollbackFor = ApiException.class)
  AppUser login(Login input) {
    if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.unauthorized();
    Optional<AppUser> found = users.lockByEmail(normalizeEmail(input.email()));
    if (found.isEmpty()) {
      passwords.matches(input.password(), dummyHash);
      throw ApiException.unauthorized();
    }
    AppUser user = found.get();
    if (user.lockedUntil != null && user.lockedUntil.isAfter(Instant.now())) {
      passwords.matches(input.password(), dummyHash);
      throw ApiException.unauthorized();
    }
    if (!passwords.matches(input.password(), user.passwordHash)) {
      fail(user);
      throw ApiException.unauthorized();
    }
    if (user.mfaEnabled) {
      if (input.code() == null)
        throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "MFA_REQUIRED",
            "Enter the six-digit code from your authenticator app.");
      long step = totp.verify(user.mfaSecret, input.code(), user.lastMfaStep);
      if (step < 0) {
        fail(user);
        throw ApiException.unauthorized();
      }
      user.lastMfaStep = step;
    }
    user.failedLogins = 0;
    user.lockedUntil = null;
    users.save(user);
    audit.record(user.id, "USER_LOGIN", user.id, "Successful sign-in");
    return user;
  }

  private void fail(AppUser user) {
    user.failedLogins++;
    if (user.failedLogins >= 5) {
      user.lockedUntil = Instant.now().plus(Duration.ofMinutes(15));
      user.failedLogins = 0;
    }
    users.save(user);
    audit.record(user.id, "LOGIN_FAILED", user.id, "Sign-in failed");
  }

  @Transactional(readOnly = true)
  AppUser user(UUID id) {
    return users.findById(id).orElseThrow(ApiException::missing);
  }

  @Transactional
  UserView settings(UUID id, Settings input) {
    AppUser user = users.lockById(id).orElseThrow(ApiException::missing);
    user.firstName = input.firstName().trim();
    user.lastName = input.lastName().trim();
    audit.record(id, "PROFILE_UPDATED", id, "Profile name updated");
    return UserView.of(user);
  }

  @Transactional
  MfaSetup setup(UUID id, Password input) {
    AppUser user = confirmed(id, input.password());
    if (user.mfaEnabled)
      throw ApiException.invalid("Disable MFA before setting up a new authenticator.");
    String secret = totp.createSecret();
    user.mfaSecret = totp.encrypt(secret);
    user.lastMfaStep = -1;
    audit.record(id, "MFA_SETUP", id, "Authenticator setup initiated");
    String label = URLEncoder.encode("WealthPath:" + user.email, StandardCharsets.UTF_8);
    return new MfaSetup(
        secret,
        "otpauth://totp/"
            + label
            + "?secret="
            + secret
            + "&issuer=WealthPath&algorithm=SHA1&digits=6&period=30");
  }

  @Transactional
  UserView toggle(UUID id, MfaConfirm input, boolean enable) {
    AppUser user = confirmed(id, input.password());
    if (user.mfaSecret == null) throw ApiException.invalid("Set up an authenticator first.");
    if (enable && user.mfaEnabled) throw ApiException.invalid("MFA is already enabled.");
    if (!enable && !user.mfaEnabled) throw ApiException.invalid("MFA is already disabled.");
    long step = totp.verify(user.mfaSecret, input.code(), user.lastMfaStep);
    if (step < 0)
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "INVALID_MFA_CODE",
          "The verification code is invalid or has already been used.");
    user.lastMfaStep = step;
    user.mfaEnabled = enable;
    if (!enable) {
      user.mfaSecret = null;
      user.lastMfaStep = -1;
    }
    audit.record(id, enable ? "MFA_ENABLED" : "MFA_DISABLED", id, "Authenticator settings changed");
    return UserView.of(user);
  }

  private AppUser confirmed(UUID id, String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.unauthorized();
    AppUser user = users.lockById(id).orElseThrow(ApiException::missing);
    if (!passwords.matches(password, user.passwordHash)) throw ApiException.unauthorized();
    return user;
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  static void checkPasswordBytes(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("Password must be no more than 72 UTF-8 bytes.");
  }
}
