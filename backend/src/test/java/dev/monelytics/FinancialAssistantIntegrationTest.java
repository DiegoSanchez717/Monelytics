package dev.monelytics;

import static dev.monelytics.ApiTestSupport.realCsrf;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialAssistantIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void assistantUsesOwnedLedgerAndDoesNotCreateTransactions() throws Exception {
    Cookie owner = register();
    String account =
        create(
                owner,
                "/accounts",
                Map.of("name", "Cash", "type", "CHECKING", "openingBalance", 3000))
            .get("id")
            .asText();
    String category =
        create(
                owner,
                "/categories",
                Map.of("name", "Assistant groceries", "type", "EXPENSE", "color", "#48D1CC"))
            .get("id")
            .asText();
    create(
        owner,
        "/transactions",
        Map.of(
            "accountId",
            account,
            "type",
            "INCOME",
            "amount",
            3000,
            "date",
            LocalDate.now(ZoneOffset.UTC).toString(),
            "description",
            "Salary"));
    create(
        owner,
        "/transactions",
        Map.of(
            "accountId",
            account,
            "categoryId",
            category,
            "type",
            "EXPENSE",
            "amount",
            500,
            "date",
            LocalDate.now(ZoneOffset.UTC).toString(),
            "description",
            "Food"));
    create(
        owner,
        "/budgets",
        Map.of(
            "categoryId",
            category,
            "month",
            YearMonth.now(ZoneOffset.UTC).toString(),
            "limitAmount",
            1000));
    var reply =
        mvc.perform(
                post("/api/assistant/chat")
                    .cookie(owner)
                    .with(realCsrf(mvc, json))
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "question",
                                "Can I afford this purchase?",
                                "purchaseAmount",
                                100,
                                "categoryId",
                                category))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("LIKELY_AFFORDABLE"))
            .andExpect(jsonPath("$.provider").value("MOCK"))
            .andExpect(jsonPath("$.readOnly").value(true))
            .andExpect(jsonPath("$.disclaimer").value(FinancialAssistantService.DISCLAIMER))
            .andReturn();
    JsonNode body = json.readTree(reply.getResponse().getContentAsString());
    List<String> factors = new ArrayList<>();
    body.get("factors")
        .forEach(
            factor ->
                factors.add(
                    factor.get("label").asText()
                        + ":"
                        + factor.get("value").decimalValue().stripTrailingZeros().toPlainString()));
    assertThat(factors)
        .contains(
            "Recorded monthly income:3000",
            "Recorded monthly expenses:500",
            "Applicable budget room:500",
            "Cushion after purchase:400");
    mvc.perform(get("/api/transactions").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    Cookie other = register();
    mvc.perform(
            post("/api/assistant/chat")
                .cookie(other)
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("question", "Analyze", "categoryId", category))))
        .andExpect(status().isNotFound());
  }

  @Test
  void newUserGetsInsufficientDataAndRequestsAreValidatedAndProtected() throws Exception {
    Cookie owner = register();
    String request =
        json.writeValueAsString(Map.of("question", "Could I buy this?", "purchaseAmount", 20));
    mvc.perform(
            post("/api/assistant/chat")
                .cookie(owner)
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").value("INSUFFICIENT_DATA"));
    mvc.perform(
            post("/api/assistant/chat")
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content(request))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/assistant/chat")
                .cookie(owner)
                .contentType("application/json")
                .content(request))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/assistant/chat")
                .cookie(owner)
                .with(realCsrf(mvc, json))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("question", "", "purchaseAmount", -1))))
        .andExpect(status().isBadRequest());
  }

  private Cookie register() throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/register")
                    .with(realCsrf(mvc, json))
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "firstName",
                                "Assistant",
                                "lastName",
                                "Test",
                                "email",
                                "assistant-" + UUID.randomUUID() + "@test.dev",
                                "password",
                                "AssistantTest!2026"))))
            .andExpect(status().isCreated())
            .andReturn();
    return Arrays.stream(result.getResponse().getCookies())
        .filter(cookie -> cookie.getName().equals("MONELYTICS_SESSION"))
        .findFirst()
        .orElseThrow();
  }

  private JsonNode create(Cookie owner, String path, Object input) throws Exception {
    var result =
        mvc.perform(
                post("/api" + path)
                    .cookie(owner)
                    .with(realCsrf(mvc, json))
                    .contentType("application/json")
                    .content(json.writeValueAsString(input)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }
}
