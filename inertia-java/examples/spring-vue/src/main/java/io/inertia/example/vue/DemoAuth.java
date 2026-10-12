package io.inertia.example.vue;

import io.inertia.core.*;
import io.inertia.spring.InertiaSessionStoreFactory;
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
      HttpSecurity http,
      PageCodec codec,
      InertiaSessionStoreFactory stores,
      Environment environment)
      throws Exception {
    String fixation = environment.getProperty("inertia.demo-fixation", "new-session");
    if (!Set.of("new-session", "change-session-id").contains(fixation))
      throw new IllegalArgumentException("Unsupported demo session fixation strategy");
    boolean rotate = fixation.equals("change-session-id");
    if (rotate && !(stores instanceof io.inertia.boot.RedisHttpSessionStoreFactory))
      throw new IllegalArgumentException("The rotation demo requires the Redis lifecycle factory");
    return http.authorizeHttpRequests(
            routes ->
                routes
                    .requestMatchers("/admin")
                    .hasRole("ADMIN")
                    .requestMatchers("/account")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
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
                            stores,
                            Map.of("/users", "/users", "/login", "/login", "/logout", "/account"))))
        .sessionManagement(
            session ->
                session.sessionFixation(
                    strategy -> {
                      if (rotate) strategy.changeSessionId();
                      else strategy.newSession();
                    }))
        .formLogin(
            login ->
                login
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          // Spring creates a clean session at the identity boundary.
                          try {
                            var context =
                                new InertiaContext(
                                    new InertiaRequest(
                                        "POST",
                                        java.net.URI.create("http://localhost/login"),
                                        Map.of()),
                                    stores.create(request),
                                    codec);
                            context.clearHistory().commitRedirect();
                          } catch (RuntimeException failure) {
                            rejectAuthentication(request, response);
                            throw failure;
                          }
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
                                  stores.create(request),
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

  private static void rejectAuthentication(
      HttpServletRequest request, HttpServletResponse response) {
    // Security saves authentication before invoking the success handler. Remove it even when
    // delivery storage fails; an error response must not leave a successful login behind.
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    new org.springframework.security.web.context.HttpSessionSecurityContextRepository()
        .saveContext(
            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext(),
            request,
            response);
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
