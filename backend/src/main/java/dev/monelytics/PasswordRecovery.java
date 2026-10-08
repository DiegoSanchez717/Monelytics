package dev.monelytics;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;

@Entity
@Table(name = "password_resets")
class PasswordReset {
  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  AppUser user;

  @Column(nullable = false, unique = true, length = 64)
  String tokenHash;

  @Column(nullable = false)
  Instant createdAt = Instant.now();

  @Column(nullable = false)
  Instant expiresAt;

  Instant usedAt;
}

interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
  @EntityGraph(attributePaths = "user")
  Optional<PasswordReset> findByTokenHash(String hash);

  Optional<PasswordReset> findFirstByUserIdOrderByCreatedAtDesc(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from PasswordReset r where r.tokenHash=:hash")
  Optional<PasswordReset> lockToken(@Param("hash") String hash);

  @Modifying
  @Query("update PasswordReset r set r.usedAt=:now where r.user.id=:id and r.usedAt is null")
  void invalidate(@Param("id") UUID id, @Param("now") Instant now);
}

interface ResetMailGateway {
  void send(String email, String link);
}

@Configuration
@EnableAsync
class ResetMailConfiguration {
  @Bean("resetMailExecutor")
  Executor resetMailExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("password-mail-");
    executor.initialize();
    return executor;
  }
}

@Component
class SmtpResetMailGateway implements ResetMailGateway {
  private static final Logger log = LoggerFactory.getLogger(SmtpResetMailGateway.class);
  private final JavaMailSender sender;
  private final String from;

  SmtpResetMailGateway(JavaMailSender sender, @Value("${monelytics.mail-from}") String from) {
    this.sender = sender;
    this.from = from;
  }

  @Async("resetMailExecutor")
  public void send(String email, String link) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(email);
      message.setSubject("Reset your Monelytics password");
      message.setText(
          "Use this single-use link within 30 minutes:\n"
              + link
              + "\n\nIf you did not request a reset, ignore this message. Your MFA settings remain enabled.");
      sender.send(message);
    } catch (RuntimeException failure) {
      // Never put recipients, reset tokens, mail bodies, or SMTP credentials in logs.
      log.warn("Password recovery email could not be delivered; check SMTP configuration.");
    }
  }
}

@Service
class PasswordRecoveryService {
  static final String REQUEST_MESSAGE =
      "If that email belongs to an account, a password reset link will be sent.";
  static final String INVALID_MESSAGE =
      "This password reset link is invalid or expired. Request a new link.";
  private final UserRepository users;
  private final PasswordResetRepository resets;
  private final PasswordEncoder encoder;
  private final ResetMailGateway mail;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final String baseUrl;
  private final SecureRandom random = new SecureRandom();

  PasswordRecoveryService(
      UserRepository users,
      PasswordResetRepository resets,
      PasswordEncoder encoder,
      ResetMailGateway mail,
      JdbcTemplate jdbc,
      AuditService audit,
      @Value("${monelytics.public-base-url}") String baseUrl) {
    this.users = users;
    this.resets = resets;
    this.encoder = encoder;
    this.mail = mail;
    this.jdbc = jdbc;
    this.audit = audit;
    var uri = java.net.URI.create(baseUrl);
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null)
      throw new IllegalArgumentException("Configure a valid PUBLIC_BASE_URL.");
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  @Transactional
  public void request(String email) {
    Optional<AppUser> found = users.lockByEmail(AuthService.normalizeEmail(email));
    if (found.isEmpty())
      return; // The same generic response covers unknown and throttled addresses.
    AppUser user = found.get();
    Instant now = Instant.now();
    if (resets
        .findFirstByUserIdOrderByCreatedAtDesc(user.id)
        .filter(reset -> reset.createdAt.isAfter(now.minusSeconds(60)))
        .isPresent()) return;
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    resets.invalidate(user.id, now);
    PasswordReset reset = new PasswordReset();
    reset.user = user;
    reset.tokenHash = hash(token);
    reset.expiresAt = now.plusSeconds(1800);
    resets.saveAndFlush(reset);
    audit.record(user.id, "PASSWORD_RESET_REQUESTED", user.id, "Recovery link requested");
    // Send only after commit, asynchronously: HTTP responses never expose tokens or delivery state.
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              mail.send(user.email, baseUrl + "/reset-password?token=" + token);
            } catch (RuntimeException ignored) {
              /* A saturated mail queue must not reveal account existence. */
            }
          }
        });
  }

  @Transactional
  public void confirm(String token, String password) {
    AuthService.checkPasswordBytes(password);
    String hash = hash(token);
    PasswordReset candidate =
        resets.findByTokenHash(hash).orElseThrow(() -> ApiException.invalid(INVALID_MESSAGE));
    // Acquire the user lock first, consistently with request(), so concurrent resets cannot replay.
    AppUser user =
        users.lockById(candidate.user.id).orElseThrow(() -> ApiException.invalid(INVALID_MESSAGE));
    PasswordReset reset =
        resets.lockToken(hash).orElseThrow(() -> ApiException.invalid(INVALID_MESSAGE));
    Instant now = Instant.now();
    if (reset.usedAt != null || !reset.expiresAt.isAfter(now))
      throw ApiException.invalid(INVALID_MESSAGE);
    user.passwordHash = encoder.encode(password);
    user.failedLogins = 0;
    user.lockedUntil = null;
    resets.invalidate(user.id, now);
    // Revoke every existing browser session. Recovery never bypasses or removes MFA.
    jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", user.id.toString());
    audit.record(
        user.id, "PASSWORD_RESET_COMPLETED", user.id, "Password changed; sessions revoked");
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}

@RestController
@RequestMapping("/api/auth")
class PasswordRecoveryController {
  record Request(@NotBlank @Email @Size(max = 254) String email) {
    @Override
    public String toString() {
      return "ResetRequest[redacted]";
    }
  }

  record Confirm(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
      @NotBlank
          @Size(min = 12, max = 72)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$",
              message = "must contain upper and lower case letters, a number, and a symbol")
          String password) {
    @Override
    public String toString() {
      return "ResetConfirm[credentials redacted]";
    }
  }

  private final PasswordRecoveryService service;

  PasswordRecoveryController(PasswordRecoveryService service) {
    this.service = service;
  }

  @PostMapping("/forgot-password")
  Map<String, String> request(@Valid @RequestBody Request input) {
    service.request(input.email());
    return Map.of("message", PasswordRecoveryService.REQUEST_MESSAGE);
  }

  @PostMapping("/reset-password")
  Map<String, String> confirm(
      @Valid @RequestBody Confirm input, jakarta.servlet.http.HttpServletRequest request) {
    service.confirm(input.token(), input.password());
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    return Map.of(
        "message",
        "Your password was reset. Sign in with your new password and authenticator if enabled.");
  }
}
