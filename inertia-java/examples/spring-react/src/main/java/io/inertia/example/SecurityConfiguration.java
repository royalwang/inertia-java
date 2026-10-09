package io.inertia.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.util.StringUtils;

/** Demo routes are public; unsafe requests still require a browser CSRF token. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      io.inertia.core.PageCodec codec,
      io.inertia.boot.InertiaProperties properties)
      throws Exception {
    return http.authorizeHttpRequests(routes -> routes.anyRequest().permitAll())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new BrowserCsrfHandler()))
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    new BrowserCsrfFailureHandler(codec, properties.sessionNamespace())))
        .formLogin(login -> login.disable())
        .httpBasic(basic -> basic.disable())
        .build();
  }

  static final class BrowserCsrfHandler implements CsrfTokenRequestHandler {
    private final CsrfTokenRequestHandler masked = new XorCsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(
        HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
      masked.handle(request, response, token);
      token.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
      return (StringUtils.hasText(request.getHeader(token.getHeaderName())) ? plain : masked)
          .resolveCsrfTokenValue(request, token);
    }
  }
}
