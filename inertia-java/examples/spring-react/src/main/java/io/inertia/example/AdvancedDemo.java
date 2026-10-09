package io.inertia.example;

import io.inertia.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/** Deterministic snapshots/deltas and independently scoped forms for client protocol examples. */
@Controller
public class AdvancedDemo {
  private final AtomicInteger expensiveCalls = new AtomicInteger();
  private final AtomicInteger optionalCalls = new AtomicInteger();

  @GetMapping("/advanced")
  InertiaResponse page(InertiaRequest request, @RequestParam(defaultValue = "0") int phase) {
    if (phase < 0 || phase > 2)
      return new InertiaResponse("Error", Props.builder().put("status", 400).build()).status(400);
    Map<String, Object> profile;
    if (phase == 0)
      profile =
          Map.of(
              "name",
              "Ada",
              "preferences",
              Map.of("theme", "light", "language", "en"),
              "members",
              List.of(Map.of("id", 1, "name", "Ada"), Map.of("id", 2, "name", "Linus")));
    else if (request.isPartial("Advanced"))
      profile =
          Map.of(
              "preferences",
              Map.of("theme", "dark"),
              "members",
              List.of(Map.of("id", 2, "name", "Linus updated"), Map.of("id", 3, "name", "Grace")));
    else
      profile =
          Map.of(
              "name",
              "Ada",
              "preferences",
              Map.of("theme", "dark", "language", "en"),
              "members",
              List.of(
                  Map.of("id", 1, "name", "Ada"),
                  Map.of("id", 2, "name", "Linus updated"),
                  Map.of("id", 3, "name", "Grace")));
    return new InertiaResponse(
            "Advanced",
            Props.builder()
                .put("profile", Prop.value(profile).deepMerge().matchOn("members.id"))
                .put("expensive", Prop.lazy(expensiveCalls::incrementAndGet))
                .put("optional", Prop.optional(optionalCalls::incrementAndGet))
                .put("status", Prop.always(phase))
                .build())
        .withHeader("Cache-Control", "private, no-store");
  }

  @PostMapping("/advanced/{bag}")
  HttpOutcome save(
      InertiaContext context, @PathVariable String bag, @RequestBody Map<String, String> input) {
    if (!Set.of("profile", "team").contains(bag)) return HttpOutcome.empty(404);
    String name = input.get("name");
    if (name == null || name.isBlank())
      context.withErrors(bag, Map.of("name", "Enter a " + bag + " name."));
    else context.flash("toast", bag + " saved (demo only)");
    return ProtocolPolicy.redirect("/advanced");
  }
}
