package dev.wealthpath;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.ObjectMapper;

class RequestBodyLimitTest {
  private final RequestContextFilter filter = new RequestContextFilter(new ObjectMapper());

  @Test
  void unknownLengthOversizedJsonNeverReachesController() throws Exception {
    byte[] oversized =
        ("{\"padding\":\"" + "x".repeat(RequestContextFilter.MAX_JSON_BYTES) + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    MockHttpServletRequest request = unknownLength(oversized);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          throw new AssertionError("Oversized request reached business logic");
        });
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
  }

  @Test
  void exactLimitUnknownLengthBodyIsReplayedWithoutTruncation() throws Exception {
    byte[] body =
        ("{\"value\":\"" + "x".repeat(RequestContextFilter.MAX_JSON_BYTES - 12) + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    assertThat(body).hasSize(RequestContextFilter.MAX_JSON_BYTES);
    MockHttpServletRequest request = unknownLength(body);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (req, res) -> {
          assertThat(req.getInputStream().readAllBytes()).isEqualTo(body);
          assertThat(req.getContentLengthLong()).isEqualTo(body.length);
        });
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private MockHttpServletRequest unknownLength(byte[] body) {
    MockHttpServletRequest request =
        new MockHttpServletRequest() {
          @Override
          public int getContentLength() {
            return -1;
          }

          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setMethod("POST");
    request.setRequestURI("/api/goals/calculate");
    request.setContentType("application/json");
    request.setContent(body);
    request.addHeader("Transfer-Encoding", "chunked");
    return request;
  }
}
