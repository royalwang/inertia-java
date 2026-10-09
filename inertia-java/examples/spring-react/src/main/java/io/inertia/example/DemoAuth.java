package io.inertia.example;

import io.inertia.core.*;
import io.inertia.spring.HttpSessionStore;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Opt-in local identity demonstration; applications supply their own identity provider. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "inertia.demo-auth", havingValue = "true")
public class DemoAuth {
  @Bean
  UserDetailsService demoUsers(Environment environment) {
    String password = environment.getProperty("inertia.demo-password");
    if (password == null || password.length() < 12)
      throw new IllegalArgumentException(
          "Set inertia.demo-password with at least 12 characters for the opt-in identity demo");
    return new InMemoryUserDetailsManager(
        User.withUsername("demo")
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
            .roles("DEMO")
            .build());
  }

  @Bean
  SecurityFilterChain demoSecurity(
      HttpSecurity http, PageCodec codec, io.inertia.boot.InertiaProperties properties)
      throws Exception {
    return http.authorizeHttpRequests(
            routes -> routes.requestMatchers("/account").authenticated().anyRequest().permitAll())
        .requestCache(cache -> cache.disable())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SecurityConfiguration.BrowserCsrfHandler()))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setHeader("Cache-Control", "private, no-store");
                          response.addHeader("Vary", "X-Inertia");
                          if ("true".equals(request.getHeader("X-Inertia"))) {
                            response.setStatus(409);
                            response.setHeader(
                                "X-Inertia-Location", request.getContextPath() + "/login");
                          } else redirect(request, response, "/login");
                        })
                    .accessDeniedHandler(
                        new BrowserCsrfFailureHandler(
                            codec,
                            properties.sessionNamespace(),
                            Map.of("/users", "/users", "/login", "/login", "/logout", "/account"))))
        .sessionManagement(session -> session.sessionFixation(fixation -> fixation.newSession()))
        .formLogin(
            login ->
                login
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          // Spring creates a clean session at the identity boundary.
                          var context =
                              new InertiaContext(
                                  new InertiaRequest(
                                      "POST",
                                      java.net.URI.create("http://localhost/login"),
                                      Map.of()),
                                  new HttpSessionStore(
                                      request.getSession(), properties.sessionNamespace()),
                                  codec);
                          context.clearHistory().commitRedirect();
                          redirect(request, response, "/account");
                        })
                    .failureHandler(
                        (request, response, error) -> {
                          var context =
                              new InertiaContext(
                                  new InertiaRequest(
                                      "POST",
                                      java.net.URI.create("http://localhost/login"),
                                      Map.of()),
                                  new HttpSessionStore(
                                      request.getSession(), properties.sessionNamespace()),
                                  codec);
                          context.withErrors(
                              Map.of(
                                  "credentials",
                                  "Sign-in failed. Check your credentials and try again."));
                          context.commitRedirect();
                          redirect(request, response, "/login");
                        })
                    .permitAll())
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) ->
                            redirect(request, response, "/login")))
        .httpBasic(basic -> basic.disable())
        .build();
  }

  private static void redirect(
      HttpServletRequest request, HttpServletResponse response, String target) {
    response.setStatus(303);
    response.setHeader("Location", request.getContextPath() + target);
    response.setHeader("Cache-Control", "private, no-store");
    response.addHeader("Vary", "X-Inertia");
  }

  @Controller
  @ConditionalOnProperty(name = "inertia.demo-auth", havingValue = "true")
  static class Pages {
    @GetMapping("/login")
    InertiaResponse login(Authentication authentication) {
      return new InertiaResponse(
              "Auth/Login", Props.builder().put("signedIn", authentication != null).build())
          .clearHistory(true)
          .withHeader("Cache-Control", "private, no-store");
    }

    @GetMapping("/account")
    InertiaResponse account(Authentication authentication) {
      return new InertiaResponse(
              "Auth/Account", Props.builder().put("username", authentication.getName()).build())
          .encryptHistory(true)
          .withHeader("Cache-Control", "private, no-store");
    }
  }
}
