package dev.monelytics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

final class ApiTestSupport {
  private ApiTestSupport() {}

  static RequestPostProcessor realCsrf(MockMvc mvc, ObjectMapper json) throws Exception {
    // Exercise the production cookie/header flow. Spring's csrf() fixture replaces the shared
    // repository, making cached application contexts behave differently with test class order.
    MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    String value = json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).as("Production CSRF cookie").isNotNull();
    assertThat(json.readTree(result.getResponse().getContentAsString()).get("headerName").asText())
        .isEqualTo("X-XSRF-TOKEN");
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
}
