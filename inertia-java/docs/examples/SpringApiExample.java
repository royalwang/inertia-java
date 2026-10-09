package io.inertia.guide;

import io.inertia.core.*;
import io.inertia.spring.InertiaErrorPage;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Minimal protocol app; use spring-react for a browser UI, security and real SSR. */
@SpringBootApplication
public class SpringApiExample {
  public static void main(String[] args) {
    SpringApplication.run(SpringApiExample.class, args);
  }

  @Bean
  InertiaConfig inertiaConfig() {
    return InertiaConfig.basic("guide-v1", Set.of("Home", "Error"));
  }

  @Bean
  InertiaErrorPage inertiaErrorPage() {
    return (request, status) -> new InertiaResponse("Error",
        Props.builder().put("status", status).build()).status(status).withoutSsr();
  }

  @Controller
  static class Pages {
    @GetMapping("/guide")
    InertiaResponse home(InertiaContext context) {
      context.share("application", "Guide");
      return context.render("Home", Props.builder()
          .put("message", "Hello")
          .put("details", Prop.optional(() -> Map.of("enabled", true)))
          .build()).withoutSsr().withHeader("Cache-Control", "private, no-store");
    }

    @PutMapping("/guide")
    HttpOutcome save(InertiaContext context, @RequestBody Map<String, String> input) {
      if (input.getOrDefault("name", "").isBlank())
        context.withErrors("profile", Map.of("name", "Required"));
      else context.flash("toast", "Saved (demo only)");
      // MVC commits the pending effects and applies PUT 302 -> 303 policy.
      return ProtocolPolicy.redirect("/guide");
    }
  }

  @RestController
  static class OrdinaryApi {
    @GetMapping("/guide-api")
    Map<String, String> home() {
      return Map.of("message", "Ordinary REST");
    }
  }
}
