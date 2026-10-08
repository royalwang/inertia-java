package io.inertia.example;

import io.inertia.spring.InertiaMvcConfigurer;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Opt-in demo policy. Applications own their policy, trusted sources and nonce lifecycle. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "inertia.csp.enabled", havingValue = "true")
final class CspFilter extends OncePerRequestFilter {
  private final SecureRandom random = new SecureRandom();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    request.setAttribute(InertiaMvcConfigurer.CSP_NONCE_ATTRIBUTE, nonce);
    String development =
        Boolean.getBoolean("inertia.development")
            ? " http://127.0.0.1:15173 ws://127.0.0.1:15173"
            : "";
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'nonce-"
            + nonce
            + "' 'strict-dynamic'; style-src 'self' 'unsafe-inline'"
            + development
            + "; connect-src 'self'"
            + development
            + "; img-src 'self' data:; object-src 'none'; base-uri 'self'");
    chain.doFilter(request, response);
  }
}
