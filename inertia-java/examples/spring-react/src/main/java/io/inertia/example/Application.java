package io.inertia.example;

import io.inertia.core.*;
import io.inertia.ssr.HttpSsrGateway;
import io.inertia.ssr.SsrEndpointResolver;
import io.inertia.ssr.SsrHealthMonitor;
import io.inertia.vite.ViteAssets;
import io.inertia.vite.ViteBuild;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  io.inertia.spring.InertiaErrorPage errorPage() {
    return (request, status) ->
        new InertiaResponse("Error", Props.builder().put("status", status).build());
  }

  @Bean(destroyMethod = "close")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "inertia.ssr-health-enabled",
      havingValue = "true")
  SsrHealthMonitor ssrHealthMonitor(PageCodec codec) {
    URI renderer = URI.create(System.getProperty("inertia.ssr", "http://127.0.0.1:13714/render"));
    URI health =
        URI.create(
            System.getProperty("inertia.ssr-health", renderer.resolve("/health").toString()));
    return new SsrHealthMonitor(
            health, Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofSeconds(5), codec)
        .start();
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "inertia.benchmark-observations",
      havingValue = "true")
  InertiaObserver benchmarkObserver() {
    return new LoggingInertiaObserver();
  }

  @Bean
  InertiaConfig config(
      PageCodec codec,
      org.springframework.beans.factory.ObjectProvider<InertiaObserver> observers,
      org.springframework.core.env.Environment environment)
      throws Exception {
    Path frontend = Path.of(System.getProperty("inertia.frontend", "frontend")).toAbsolutePath();
    boolean development = Boolean.getBoolean("inertia.development");
    String rootId = InertiaConfig.requireRootId(System.getProperty("inertia.root-id", "app"));
    ViteBuild build = development ? null : new ViteBuild(frontend.resolve("dist"), codec);
    if (build != null) build.verifyClientAssets(assetStore(frontend).resolve(build.buildId()));
    var assets =
        new ViteAssets(
            frontend.resolve("dist/client/.vite/manifest.json"),
            frontend.resolve(".inertia/hot"),
            development,
            codec,
            development ? "/build/" : "/build/" + build.buildId() + "/");
    var endpoints =
        new SsrEndpointResolver(
            URI.create(System.getProperty("inertia.ssr", "http://127.0.0.1:13714/render")),
            frontend.resolve(".inertia/hot"),
            frontend.resolve("dist/ssr/ssr.js"),
            development,
            Arrays.asList(
                environment.getProperty("inertia.ssr-except", String[].class, new String[0])));
    var gateway =
        new HttpSsrGateway(
            endpoints,
            Duration.ofMillis(200),
            Duration.ofSeconds(1),
            2 * 1024 * 1024,
            16,
            codec,
            !development,
            rootId,
            observers.getIfAvailable(() -> InertiaObserver.NOOP),
            environment.getProperty("inertia.ssr-endpoint-id", "renderer"));
    var catalogLoads = new java.util.concurrent.atomic.AtomicInteger();
    return new InertiaConfig(
        development ? assets::version : build::buildId,
        rootId,
        Set.of("Users/Index", "About", "Feed", "Error", "History"),
        view ->
            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<link rel=\"icon\" href=\"data:,\"><meta name=\"inertia-root\" content=\""
                + rootId
                + "\">"
                + (view.nonce() == null
                    ? ""
                    : "<meta name=\"csp-nonce\" content=\"" + view.nonce() + "\">")
                + assets.tags("src/app.tsx", view.nonce())
                + view.head()
                + "</head><body>"
                + view.body()
                + "</body></html>",
        gateway,
        r ->
            Props.builder()
                .put("appName", "Inertia Java")
                .put(
                    "catalog",
                    Prop.lazy(() -> Map.of("load", catalogLoads.incrementAndGet()))
                        .onceAs("feed-catalog")
                        .until(Duration.ofSeconds(60)))
                .build(),
        true,
        false);
  }

  static Path assetStore(Path frontend) {
    return Path.of(
            System.getProperty(
                "inertia.asset-store", frontend.resolve(".inertia/assets").toString()))
        .toAbsolutePath();
  }

  @Bean
  org.springframework.web.servlet.config.annotation.WebMvcConfigurer staticAssets() {
    return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
      public void addResourceHandlers(
          org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/build/**")
            .addResourceLocations(
                (Boolean.getBoolean("inertia.development")
                        ? Path.of(System.getProperty("inertia.frontend", "frontend"))
                            .toAbsolutePath()
                            .resolve("dist/client")
                        : assetStore(Path.of(System.getProperty("inertia.frontend", "frontend"))))
                    .toUri()
                    .toString());
      }
    };
  }

  @Controller
  static class Pages {
    @GetMapping({"/", "/users"})
    InertiaResponse users() {
      return new InertiaResponse(
              "Users/Index",
              Props.builder()
                  .put(
                      "users",
                      List.of(Map.of("id", 1, "name", "Ada"), Map.of("id", 2, "name", "Linus")))
                  .put("largeId", 9007199254740993L)
                  .put("stats", Prop.defer(() -> Map.of("total", 2)).group("dashboard"))
                  .build())
          .withHeader("Cache-Control", "private, no-store");
    }

    public record UserForm(
        @jakarta.validation.constraints.NotBlank(message = "Please enter a name.")
            @jakarta.validation.constraints.Size(
                max = 100,
                message = "Name must be at most 100 characters.")
            @jakarta.validation.constraints.Pattern(
                regexp = "[^<>]*",
                message = "Name must not contain angle brackets.")
            String name) {}

    @PostMapping("/users")
    HttpOutcome store(
        InertiaContext inertia,
        @jakarta.validation.Valid @RequestBody UserForm form,
        org.springframework.validation.BindingResult errors) {
      if (errors.hasErrors())
        return inertia.backWithErrors(io.inertia.spring.ValidationBridge.errors(errors));
      String name = form.name().trim();
      inertia.flash("toast", "Saved " + name + " (demo only)");
      return ProtocolPolicy.redirect("/users");
    }

    @GetMapping("/feed")
    InertiaResponse feed(@RequestParam(defaultValue = "1") int page) {
      if (page < 1 || page > 3)
        return new InertiaResponse("Error", Props.builder().put("status", 400).build()).status(400);
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

    @GetMapping("/about")
    InertiaResponse about() {
      return new InertiaResponse("About", Props.empty());
    }

    @GetMapping("/missing")
    InertiaResponse missing() {
      return new InertiaResponse("Error", Props.builder().put("status", 404).build()).status(404);
    }
  }

  @RestController
  static class Health {
    private final org.springframework.beans.factory.ObjectProvider<SsrHealthMonitor> ssr;

    Health(org.springframework.beans.factory.ObjectProvider<SsrHealthMonitor> ssr) {
      this.ssr = ssr;
    }

    @GetMapping("/api/ssr-health")
    Map<String, Object> ssrHealth() {
      var monitor = ssr.getIfAvailable();
      if (monitor == null) return Map.of("state", "DISABLED");
      var snapshot = monitor.snapshot();
      var result = new LinkedHashMap<String, Object>();
      result.put("state", snapshot.state());
      result.put("reason", snapshot.reason());
      result.put("checkedAt", snapshot.checkedAt());
      return result;
    }

    @GetMapping("/api/health")
    Map<String, String> health() {
      return Map.of("status", "ok");
    }
  }
}
