package qualification;

import io.inertia.core.*;
import io.inertia.spring.InertiaSessionStoreFactory;
import io.inertia.ssr.HttpSsrGateway;
import io.inertia.vite.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

/** Standalone local qualification consumer; fixture control routes are not application features. */
@SpringBootApplication
public class Application {
  public static void main(String[] args) { SpringApplication.run(Application.class, args); }

  @Bean InertiaConfig inertiaConfig(PageCodec codec, Environment environment) throws Exception {
    Path frontend = Path.of(environment.getRequiredProperty("qualification.frontend")).toAbsolutePath();
    var build = new ViteBuild(frontend.resolve("dist"), codec); build.verifyClientAssets(frontend.resolve("dist/client"));
    var assets = new ViteAssets(frontend.resolve("dist/client/.vite/manifest.json"), frontend.resolve(".inertia/hot"), false, codec, "/build/");
    var gateway = new HttpSsrGateway(URI.create(environment.getRequiredProperty("qualification.ssr")), Duration.ofMillis(200), Duration.ofSeconds(1), 2 * 1024 * 1024, 16, codec, true, "app");
    return new InertiaConfig(build::buildId, "app", Set.of("Users/Index", "About"),
        view -> "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><link rel=\"icon\" href=\"data:,\">"
            + assets.tags("src/app.tsx", null) + view.head() + "</head><body>" + view.body() + "</body></html>",
        gateway, request -> Props.empty(), true, false);
  }

  @Bean WebMvcConfigurer assets(Environment environment) {
    return new WebMvcConfigurer() {
      @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/build/**").addResourceLocations(Path.of(environment.getRequiredProperty("qualification.frontend")).resolve("dist/client").toUri().toString());
      }
    };
  }

  @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> nodeHeader(Environment environment) {
    var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter>(new org.springframework.web.filter.OncePerRequestFilter() {
      @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws java.io.IOException, ServletException {
        response.setHeader("X-Qualification-Node", environment.getRequiredProperty("qualification.node")); chain.doFilter(request, response);
      }
    });
    registration.setOrder(Integer.MIN_VALUE + 200); return registration;
  }

  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
    var plain = new CsrfTokenRequestAttributeHandler();
    return http.authorizeHttpRequests(routes -> routes.anyRequest().permitAll())
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new CsrfTokenRequestHandler() {
          @Override public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) { plain.handle(request, response, token); token.get(); }
          @Override public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) { return plain.resolveCsrfTokenValue(request, token); }
        })).formLogin(login -> login.disable()).httpBasic(basic -> basic.disable()).build();
  }

  @Controller
  static class Pages {
    private final InertiaSessionStoreFactory stores;
    private final Map<String, SessionStore.Delivery> held = new ConcurrentHashMap<>();
    Pages(InertiaSessionStoreFactory stores) { this.stores = stores; }
    @GetMapping({"/", "/users"}) public InertiaResponse users() {
      return new InertiaResponse("Users/Index", Props.builder().put("users", List.of(Map.of("id", 1, "name", "Ada")))
          .put("largeId", 9007199254740993L).put("stats", Prop.defer(() -> Map.of("total", 1))).build());
    }
    @GetMapping("/about") public InertiaResponse about() { return new InertiaResponse("About", Props.empty()); }
    @PostMapping("/users") public HttpOutcome save(InertiaContext context, @RequestBody Map<String, String> form) {
      String name = form.getOrDefault("name", "").trim();
      if (name.isEmpty()) context.withErrors(Map.of("name", "Please enter a name."));
      else context.flash("toast", "Saved " + name + " across nodes");
      return ProtocolPolicy.redirect("/users");
    }
    @GetMapping("/fixture/seed") public HttpOutcome seed(InertiaContext context, @RequestParam(defaultValue = "cross-node") String value) {
      context.flash("toast", value); context.withErrors("form-a", Map.of("name", List.of("first", "second")));
      context.withErrors("form-b", Map.of("email", "required")); return ProtocolPolicy.redirect("/users");
    }
    @GetMapping("/fixture/hold") @ResponseBody public Map<String, Boolean> hold(HttpServletRequest request) {
      var store = stores.create(request); held.put(request.getSession().getId(), store.beginPageDelivery()); return Map.of("held", true);
    }
    @GetMapping("/fixture/complete") @ResponseBody public Map<String, Boolean> complete(HttpServletRequest request) {
      var delivery = held.get(request.getSession().getId()); if (delivery == null) throw new IllegalStateException("No local held delivery");
      stores.create(request).completePageDelivery(delivery); return Map.of("completed", true);
    }
    @GetMapping("/fixture/rotate") @ResponseBody public Map<String, Boolean> rotate(HttpServletRequest request) { request.changeSessionId(); return Map.of("rotated", true); }
    @GetMapping("/fixture/invalidate") @ResponseBody public Map<String, Boolean> invalidate(HttpServletRequest request) { request.getSession().invalidate(); return Map.of("invalidated", true); }
  }
}
