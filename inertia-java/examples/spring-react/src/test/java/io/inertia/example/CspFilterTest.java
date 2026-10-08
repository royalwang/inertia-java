package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.spring.InertiaMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class CspFilterTest {
  @Test
  void generatesFreshServerNonceAndMatchesPolicy() throws Exception {
    var filter = new CspFilter();
    String previous = null;
    for (int i = 0; i < 2; i++) {
      var request = new MockHttpServletRequest("GET", "/users");
      request.addHeader("X-CSP-Nonce", "client_supplied_0123456789");
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (req, res) -> {});
      String nonce = (String) request.getAttribute(InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE);
      assertTrue(nonce.matches("[A-Za-z0-9_-]{43}"));
      assertNotEquals("client_supplied_0123456789", nonce);
      assertNotEquals(previous, nonce);
      assertTrue(
          response
              .getHeader("Content-Security-Policy")
              .contains("script-src 'nonce-" + nonce + "' 'strict-dynamic'"));
      previous = nonce;
    }
  }
}
