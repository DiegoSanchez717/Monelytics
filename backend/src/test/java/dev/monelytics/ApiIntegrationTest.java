package dev.monelytics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired TotpService totp;
  private final String password = "StrongPath!2026";

  private RequestPostProcessor token() throws Exception {
    MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    String value = json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return request -> {
      List<Cookie> cookies = new ArrayList<>();
      if (request.getCookies() != null) cookies.addAll(Arrays.asList(request.getCookies()));
      cookies.removeIf(item -> item.getName().equals("XSRF-TOKEN"));
      cookies.add(cookie);
      request.setCookies(cookies.toArray(Cookie[]::new));
      request.addHeader("X-XSRF-TOKEN", value);
      return request;
    };
  }

  @Test
  void anonymousProtectedApiRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/accounts"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void publicOpenApiDocumentsSecurityAndFinanceEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
        .andExpect(jsonPath("$.info.title").value("Monelytics API"))
        .andExpect(jsonPath("$.paths['/api/accounts'].get").exists())
        .andExpect(jsonPath("$.paths['/api/transactions'].post").exists())
        .andExpect(
            jsonPath("$.components.securitySchemes.session.name").value("MONELYTICS_SESSION"))
        .andExpect(jsonPath("$.components.securitySchemes.csrf.name").value("X-XSRF-TOKEN"));
  }

  @Test
  void frameworkClientErrorsKeepTheirHttpStatusAndSafeDetails() throws Exception {
    Cookie session = register("framework");
    mvc.perform(get("/api/resource-that-does-not-exist").cookie(session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("The requested resource was not found."));
    mvc.perform(
            post("/api/auth/me")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(
            post("/api/goals/calculate")
                .cookie(session)
                .with(token())
                .contentType("text/plain")
                .content("invalid"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.detail").value("Use application/json for this request."));
  }

  @Test
  void oversizedJsonIsRejectedWithCorrelationId() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"padding\":\"" + "x".repeat(RequestContextFilter.MAX_JSON_BYTES) + "\"}"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void mutationsRequireCsrfToken() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(registerBody("csrf")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
  }

  @Test
  void serverRejectsWeakPasswords() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .with(token())
                .contentType("application/json")
                .content(registerBody("weak").replace(password, "twelveletters")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  @Test
  void failedLoginsPersistLockoutAndDoNotRevealAccountState() throws Exception {
    String label = "lockout";
    register(label);
    String bad = "{\"email\":\"" + email(label) + "\",\"password\":\"IncorrectPath!2026\"}";
    for (int i = 0; i < 5; i++)
      mvc.perform(
              post("/api/auth/login").with(token()).contentType("application/json").content(bad))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    String valid = "{\"email\":\"" + email(label) + "\",\"password\":\"" + password + "\"}";
    mvc.perform(
            post("/api/auth/login").with(token()).contentType("application/json").content(valid))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    AppUser user = users.findByEmail(email(label)).orElseThrow();
    assertThat(user.lockedUntil).isAfter(Instant.now());
    user.lockedUntil = Instant.now().minusSeconds(1);
    users.save(user);
    mvc.perform(
            post("/api/auth/login").with(token()).contentType("application/json").content(valid))
        .andExpect(status().isOk());
  }

  @Test
  void sessionPersistsAndLogoutRevokesAccess() throws Exception {
    Cookie session = register("session");
    assertThat(session.isHttpOnly()).isTrue();
    assertThat(session.getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(session.getPath()).isEqualTo("/");
    mvc.perform(get("/api/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Casey"));
    mvc.perform(post("/api/auth/logout").cookie(session).with(token()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void ledgerEditsReversePriorEffectAndProtectFunds() throws Exception {
    Cookie session = register("ledger");
    String account = createAccount(session, "100.00");
    String transaction = createTransaction(session, account, "CONTRIBUTION", "50.25");
    assertBalance(session, "150.25");
    mvc.perform(
            put("/api/transactions/" + transaction)
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content(transactionBody(account, "CONTRIBUTION", "75.50")))
        .andExpect(status().isOk());
    assertBalance(session, "175.50");
    mvc.perform(
            post("/api/transactions")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content(transactionBody(account, "WITHDRAWAL", "200.00")))
        .andExpect(status().isUnprocessableEntity());
    assertBalance(session, "175.50");
    mvc.perform(delete("/api/transactions/" + transaction).cookie(session).with(token()))
        .andExpect(status().isNoContent());
    assertBalance(session, "100.00");
  }

  @Test
  void contributionPolicyAggregatesAllOwnedAccounts() throws Exception {
    Cookie session = register("limits");
    String first = createAccount(session, "0.00"), second = createAccount(session, "0.00");
    createTransaction(session, first, "CONTRIBUTION", "6000.00");
    mvc.perform(
            post("/api/transactions")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content(transactionBody(second, "CONTRIBUTION", "1000.01")))
        .andExpect(status().isUnprocessableEntity());
    createTransaction(session, second, "CONTRIBUTION", "1000.00");
    mvc.perform(get("/api/dashboard").cookie(session))
        .andExpect(jsonPath("$.annualContributions").value(7000));
  }

  @Test
  void ownershipAndRolesAreEnforcedOnServer() throws Exception {
    Cookie owner = register("owner"), other = register("other");
    String account = createAccount(owner, "0.00");
    mvc.perform(
            put("/api/accounts/" + account)
                .cookie(other)
                .with(token())
                .contentType("application/json")
                .content("{\"name\":\"Hijack\",\"type\":\"ROTH_IRA\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/audit").cookie(owner)).andExpect(status().isForbidden());
    mvc.perform(get("/api/accounts").cookie(other)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void beneficiariesMustTotalOneHundredAndCanBeCleared() throws Exception {
    Cookie session = register("beneficiaries");
    String account = createAccount(session, "0.00");
    String invalid =
        "{\"beneficiaries\":[{\"name\":\"Jordan\",\"relationship\":\"Spouse\",\"percentage\":80}]}";
    mvc.perform(
            put("/api/accounts/" + account + "/beneficiaries")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content(invalid))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            put("/api/accounts/" + account + "/beneficiaries")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content(invalid.replace("80", "100")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.beneficiaries[0].percentage").value(100));
    mvc.perform(
            put("/api/accounts/" + account + "/beneficiaries")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content("{\"beneficiaries\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.beneficiaries.length()").value(0));
  }

  @Test
  void searchSortPaginationAndDateValidationWork() throws Exception {
    Cookie session = register("search");
    String account = createAccount(session, "100.00");
    createTransaction(session, account, "CONTRIBUTION", "10.00");
    createTransaction(session, account, "RETURN", "20.00");
    mvc.perform(
            get("/api/transactions")
                .cookie(session)
                .param("search", "test entry")
                .param("sort", "amount,desc")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content[0].amount").value(20));
    mvc.perform(get("/api/transactions").cookie(session).param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/transactions").cookie(session).param("sort", "passwordHash,asc"))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void mfaSetupEnableLoginChallengeAndReplayProtection() throws Exception {
    String label = "mfa";
    Cookie session = register(label);
    String email = email(label);
    MvcResult setup =
        mvc.perform(
                post("/api/auth/mfa/setup")
                    .cookie(session)
                    .with(token())
                    .contentType("application/json")
                    .content("{\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String secret = json.readTree(setup.getResponse().getContentAsString()).get("secret").asText();
    long step = Instant.now().getEpochSecond() / 30;
    String code = totp.code(secret, step);
    mvc.perform(
            post("/api/auth/mfa/enable")
                .cookie(session)
                .with(token())
                .contentType("application/json")
                .content("{\"password\":\"" + password + "\",\"code\":\"" + code + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaEnabled").value(true));
    String login = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    mvc.perform(
            post("/api/auth/login").with(token()).contentType("application/json").content(login))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
    String withCode =
        login.substring(0, login.length() - 1)
            + ",\"code\":\""
            + totp.code(secret, step + 1)
            + "\"}";
    mvc.perform(
            post("/api/auth/login").with(token()).contentType("application/json").content(withCode))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/login").with(token()).contentType("application/json").content(withCode))
        .andExpect(status().isUnauthorized());
    AppUser user = users.findByEmail(email).orElseThrow();
    assertThat(user.mfaSecret).doesNotContain(secret);
  }

  private String email(String label) {
    return label + "-" + runId + "@test.dev";
  }

  private final String runId = UUID.randomUUID().toString();

  private String registerBody(String label) {
    return "{\"firstName\":\"Casey\",\"lastName\":\"Rivera\",\"email\":\""
        + email(label)
        + "\",\"password\":\""
        + password
        + "\"}";
  }

  private Cookie register(String label) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/register")
                    .with(token())
                    .contentType("application/json")
                    .content(registerBody(label)))
            .andExpect(status().isCreated())
            .andReturn();
    return Arrays.stream(result.getResponse().getCookies())
        .filter(c -> c.getName().equals("MONELYTICS_SESSION"))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Session cookie missing: " + result.getResponse().getHeaders("Set-Cookie")));
  }

  private String createAccount(Cookie session, String balance) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/accounts")
                    .cookie(session)
                    .with(token())
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"Test IRA\",\"type\":\"ROTH_IRA\",\"openingBalance\":"
                            + balance
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String transactionBody(String account, String type, String amount) {
    return "{\"accountId\":\""
        + account
        + "\",\"type\":\""
        + type
        + "\",\"amount\":"
        + amount
        + ",\"description\":\"Test entry\",\"date\":\""
        + LocalDate.now(ZoneOffset.UTC)
        + "\"}";
  }

  private String createTransaction(Cookie session, String account, String type, String amount)
      throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/transactions")
                    .cookie(session)
                    .with(token())
                    .contentType("application/json")
                    .content(transactionBody(account, type, amount)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void assertBalance(Cookie session, String expected) throws Exception {
    MvcResult result =
        mvc.perform(get("/api/accounts").cookie(session)).andExpect(status().isOk()).andReturn();
    assertThat(
            json.readTree(result.getResponse().getContentAsString())
                .get(0)
                .get("balance")
                .decimalValue())
        .isEqualByComparingTo(expected);
  }
}
