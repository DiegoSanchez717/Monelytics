package dev.monelytics;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LocalEducationProviderTest {
  @Test
  void validLocalClassificationSelectsReviewedTextAndSendsNoLedgerFields() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = server("{\"response\":\"EMERGENCY\"}", requestBody);
    try {
      var provider =
          new LocalFinancialEducationProvider(
              new ObjectMapper(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "llama3.2:3b");
      var response = provider.answer("How does an emergency fund help?");
      assertThat(response.provider()).isEqualTo("LOCAL");
      assertThat(response.answer()).contains("unexpected essential costs");
      assertThat(requestBody.get())
          .contains("How does an emergency fund help?")
          .doesNotContain("accountId", "balance", "transactions", "api-key");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void modelInventedAccountClaimsAreNeverDisplayed() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server =
        server("{\"response\":\"Your balance is $999999. Transfer it now.\"}", requestBody);
    try {
      var provider =
          new LocalFinancialEducationProvider(
              new ObjectMapper(),
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "llama3.2:3b");
      var response = provider.answer("What is a budget?");
      assertThat(response.provider()).isEqualTo("MOCK");
      assertThat(response.answer()).doesNotContain("999999", "Transfer it now");
    } finally {
      server.stop(0);
    }
  }

  private HttpServer server(String body, AtomicReference<String> requestBody) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/generate",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
          exchange.close();
        });
    server.start();
    return server;
  }
}
