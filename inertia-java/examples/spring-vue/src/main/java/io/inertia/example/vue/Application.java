package io.inertia.example.vue;

import io.inertia.core.*;
import io.inertia.ssr.HttpSsrGateway;
import io.inertia.vite.*;
import jakarta.servlet.http.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

/** Vue example business application using the same public server modules as React. */
@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  private static Path frontend(Environment environment) {
    return Path.of(environment.getProperty("inertia.frontend", "frontend")).toAbsolutePath();
  }

  @Bean
  InertiaConfig inertiaConfig(PageCodec codec, Environment environment) throws Exception {
    var directory = frontend(environment);
    var build = new ViteBuild(directory.resolve("dist"), codec);
    build.verifyClientAssets(directory.resolve("dist/client"));
    var assets =
        new ViteAssets(
            directory.resolve("dist/client/.vite/manifest.json"),
            directory.resolve(".inertia/hot"),
            false,
            codec,
            "/build/");
    var gateway =
        new HttpSsrGateway(
            URI.create(environment.getProperty("inertia.ssr", "http://127.0.0.1:13715/render")),
            Duration.ofMillis(200),
            Duration.ofSeconds(1),
            2 * 1024 * 1024,
            16,
            codec,
            true,
            "app");
    var catalogLoads = new AtomicInteger();
    return new InertiaConfig(
        build::buildId,
        "app",
        Set.of("Users", "About", "Feed"),
        view ->
            "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><link rel=\"icon\" href=\"data:,\">"
                + assets.tags("src/app.ts", view.nonce())
                + view.head()
                + "</head><body>"
                + view.body()
                + "</body></html>",
        gateway,
        request ->
            Props.builder()
                .put(
                    "catalog",
                    Prop.lazy(() -> Map.of("load", catalogLoads.incrementAndGet()))
                        .onceAs("vue-catalog")
                        .until(Duration.ofSeconds(60)))
                .build(),
        true,
        false);
  }

  @Bean
  WebMvcConfigurer assets(Environment environment) {
    return new WebMvcConfigurer() {
      @Override
      public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/build/**")
            .addResourceLocations(frontend(environment).resolve("dist/client").toUri().toString());
      }
    };
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    var plain = new CsrfTokenRequestAttributeHandler();
    return http.authorizeHttpRequests(routes -> routes.anyRequest().permitAll())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(
                        new CsrfTokenRequestHandler() {
                          @Override
                          public void handle(
                              HttpServletRequest request,
                              HttpServletResponse response,
                              Supplier<CsrfToken> token) {
                            plain.handle(request, response, token);
                            token.get();
                          }

                          @Override
                          public String resolveCsrfTokenValue(
                              HttpServletRequest request, CsrfToken token) {
                            return plain.resolveCsrfTokenValue(request, token);
                          }
                        }))
        .formLogin(login -> login.disable())
        .httpBasic(basic -> basic.disable())
        .build();
  }

  @Controller
  static class Pages {
    @GetMapping({"/", "/users"})
    InertiaResponse users(@RequestParam(defaultValue = "0") int phase) {
      return new InertiaResponse(
          "Users",
          Props.builder()
              .put("users", List.of(Map.of("id", 1, "name", "Ada")))
              .put("stats", Prop.defer(() -> Map.of("total", 1)))
              .put("phase", phase)
              .put("optional", Prop.optional(() -> "Loaded optional value"))
              .build());
    }

    @PostMapping("/users")
    HttpOutcome save(InertiaContext context, @RequestBody Map<String, String> form) {
      var name = form.getOrDefault("name", "").trim();
      if (name.isEmpty()) context.withErrors(Map.of("name", "Please enter a name."));
      else context.flash("toast", "Saved " + name + " (demo only)");
      return ProtocolPolicy.redirect("/users");
    }

    @GetMapping("/about")
    InertiaResponse about() {
      return new InertiaResponse("About", Props.empty());
    }

    @GetMapping("/feed")
    InertiaResponse feed(@RequestParam(defaultValue = "1") int page) {
      if (page < 1 || page > 3) throw new IllegalArgumentException("Page must be 1–3");
      var rows =
          java.util.stream.IntStream.rangeClosed((page - 1) * 3 + 1, page * 3)
              .mapToObj(id -> Map.of("id", id, "name", "Item " + id))
              .toList();
      var scroll =
          new ScrollPage(rows, page == 1 ? null : page - 1, page == 3 ? null : page + 1, page);
      return new InertiaResponse(
              "Feed", Props.builder().put("items", Prop.scroll(scroll).matchOn("data.id")).build())
          .withHeader("Cache-Control", "private, no-store");
    }
  }
}
