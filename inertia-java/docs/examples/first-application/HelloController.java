package io.inertia.example;

import io.inertia.core.HttpOutcome;
import io.inertia.core.InertiaContext;
import io.inertia.core.InertiaResponse;
import io.inertia.core.Props;
import io.inertia.core.ProtocolPolicy;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** First-application tutorial: validates input and emits flash; no persistence. */
@Controller
public class HelloController {
  @GetMapping("/hello")
  InertiaResponse hello() {
    return new InertiaResponse("Hello", Props.builder().put("message", "Hello from Java").build())
        .withHeader("Cache-Control", "private, no-store");
  }

  @PostMapping("/hello")
  HttpOutcome greet(InertiaContext inertia, @RequestBody Map<String, Object> input) {
    Object value = input.get("name");
    if (!(value instanceof String name) || name.isBlank() || name.length() > 100) {
      inertia.withErrors(Map.of("name", "Enter a name between 1 and 100 characters."));
      return ProtocolPolicy.redirect("/hello");
    }
    inertia.flash("toast", "Hello, " + ((String) value).trim());
    return ProtocolPolicy.redirect("/hello");
  }
}
