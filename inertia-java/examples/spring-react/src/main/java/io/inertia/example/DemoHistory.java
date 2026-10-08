package io.inertia.example;

import io.inertia.core.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in browser history/SSR demonstration; it does not model authentication or logout. */
@Controller
@ConditionalOnProperty(name = "inertia.demo-history-enabled", havingValue = "true")
public class DemoHistory {
  @GetMapping("/demo-history/{mode}")
  InertiaResponse history(@PathVariable String mode, InertiaContext context, HttpSession session) {
    if (!java.util.Set.of("encrypted", "plain", "clear", "csr").contains(mode))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    int visits;
    synchronized (session) {
      var previous = session.getAttribute("demo.history.visits");
      visits = previous instanceof Integer count ? count + 1 : 1;
      session.setAttribute("demo.history.visits", visits);
    }
    context.encryptHistory(true);
    var response =
        new InertiaResponse(
                "History",
                Props.builder()
                    .put("mode", mode)
                    .put("visits", visits)
                    .put("marker", "demo-history-marker")
                    .build())
            .withHeader("Cache-Control", "private, no-store");
    if (mode.equals("plain")) response.encryptHistory(false);
    if (mode.equals("clear")) context.clearHistory();
    if (mode.equals("csr")) response.withoutSsr();
    return response;
  }
}
