package dev.monelytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordRecoveryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired PasswordResetRepository resets;
  @MockitoBean ResetMailGateway mail;
  private final String password = "RecoveryTest!2026";
  private final String nextPassword = "NewPassword!2026";

  @Test
  void genericRequestDoesNotRevealUnknownAccountsOrReturnTokens() throws Exception {
    String email = "unknown-" + UUID.randomUUID() + "@test.dev";
    mvc.perform(
            post("/api/auth/forgot-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(PasswordRecoveryService.REQUEST_MESSAGE))
        .andExpect(jsonPath("$.token").doesNotExist());
    verifyNoInteractions(mail);
    mvc.perform(
            post("/api/auth/forgot-password")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isForbidden());
  }

  @Test
  void tokenIsHashedSingleUseAndRevokesSessionsWithoutDisablingMfa() throws Exception {
    String email = "reset-" + UUID.randomUUID() + "@test.dev";
    Cookie session = register(email);
    AppUser user = users.findByEmail(email).orElseThrow();
    user.mfaEnabled = true;
    user.mfaSecret = "encrypted-secret-placeholder";
    users.save(user);
    String token = request(email);
    assertThat(resets.findByTokenHash(PasswordRecoveryService.hash(token))).isPresent();
    assertThat(resets.findByTokenHash(token)).isEmpty();
    mvc.perform(
            post("/api/auth/reset-password")
                .with(csrf())
                .cookie(session)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token, "password", nextPassword))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    assertThat(users.findByEmail(email).orElseThrow().mfaEnabled).isTrue();
    mvc.perform(
            post("/api/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token, "password", nextPassword))))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", nextPassword))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
  }

  @Test
  void expiredTokensWeakPasswordsAndRequestFloodsAreRejected() throws Exception {
    String email = "expiry-" + UUID.randomUUID() + "@test.dev";
    register(email);
    String token = request(email);
    requestAgain(email);
    verify(mail, times(1)).send(eq(email), anyString());
    mvc.perform(
            post("/api/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token, "password", "weak"))))
        .andExpect(status().isBadRequest());
    PasswordReset reset = resets.findByTokenHash(PasswordRecoveryService.hash(token)).orElseThrow();
    reset.expiresAt = Instant.now().minusSeconds(1);
    resets.save(reset);
    mvc.perform(
            post("/api/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token, "password", nextPassword))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(PasswordRecoveryService.INVALID_MESSAGE));
  }

  @Test
  void newRequestInvalidatesPreviousLinksAndUnknownTokensShareSafeError() throws Exception {
    String email = "rotate-" + UUID.randomUUID() + "@test.dev";
    register(email);
    String old = request(email);
    PasswordReset reset = resets.findByTokenHash(PasswordRecoveryService.hash(old)).orElseThrow();
    reset.createdAt = Instant.now().minusSeconds(61);
    resets.save(reset);
    requestAgain(email);
    mvc.perform(
            post("/api/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", old, "password", nextPassword))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(PasswordRecoveryService.INVALID_MESSAGE));
    mvc.perform(
            post("/api/auth/reset-password")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("token", "z".repeat(43), "password", nextPassword))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(PasswordRecoveryService.INVALID_MESSAGE));
  }

  private Cookie register(String email) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/register")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "firstName",
                                "Reset",
                                "lastName",
                                "Test",
                                "email",
                                email,
                                "password",
                                password))))
            .andExpect(status().isCreated())
            .andReturn();
    return Arrays.stream(result.getResponse().getCookies())
        .filter(cookie -> cookie.getName().equals("MONELYTICS_SESSION"))
        .findFirst()
        .orElseThrow();
  }

  private void requestAgain(String email) throws Exception {
    mvc.perform(
            post("/api/auth/forgot-password")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(PasswordRecoveryService.REQUEST_MESSAGE));
  }

  private String request(String email) throws Exception {
    requestAgain(email);
    var captor = ArgumentCaptor.forClass(String.class);
    verify(mail).send(eq(email), captor.capture());
    return captor.getValue().split("token=")[1];
  }
}
