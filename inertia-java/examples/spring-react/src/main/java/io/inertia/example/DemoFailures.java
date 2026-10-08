package io.inertia.example;

import io.inertia.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in fault demonstration for error-page and browser acceptance. */
@Controller
@ConditionalOnProperty(name = "inertia.demo-failures", havingValue = "true")
public class DemoFailures {
  @GetMapping("/failures/{scenario}")
  InertiaResponse fail(@PathVariable String scenario, jakarta.servlet.http.HttpSession session) {
    return switch (scenario) {
      case "props" ->
          new InertiaResponse(
              "Users/Index",
              Props.builder()
                  .put(
                      "failure",
                      Prop.lazy(
                          () -> {
                            throw new IllegalStateException("Demo data source failure");
                          }))
                  .build());
      case "session" -> {
        session.invalidate();
        yield new InertiaResponse("Users/Index", Props.empty());
      }
      case "forbidden" -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo denial");
      default ->
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "Demo failure scenario not found");
    };
  }
}
