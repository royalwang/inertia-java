package io.inertia.guide;

import io.inertia.core.*;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/** Executable framework-independent API example; no HTTP server or JavaScript runtime. */
public final class CoreApiExample {
  public static void main(String[] args) throws Exception {
    var codec = new PageCodec();
    var config = InertiaConfig.basic("guide-v1", Set.of("Home"));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var resolver = new PropsResolver(codec, executor, Duration.ofSeconds(1), 2);
      var renderer = new ResponseRenderer(config, codec, resolver);
      var session = new MemorySessionStore(); // One demo user, never global shared user state.
      var request = new InertiaRequest("GET", URI.create("http://localhost/guide"),
          Map.of("X-Inertia", "true", "X-Inertia-Version", "guide-v1"));

      // A mutation queues effects; commit them before writing its redirect outcome.
      var mutation = new InertiaRequest("PUT", request.fullUrl(), request.headers());
      var mutationContext = new InertiaContext(mutation, session, codec);
      mutationContext.flash("toast", "Saved");
      mutationContext.commitRedirect();
      var redirect = ProtocolPolicy.after(mutation, ProtocolPolicy.redirect("/guide"));
      System.out.println("redirect=" + redirect.status());

      // An adapter performs this check before controller work or props queries.
      var early = ProtocolPolicy.before(request, config.version().get());
      if (early.isPresent()) {
        System.out.println(early.get().status());
        return;
      }
      var context = new InertiaContext(request, session, codec);
      context.share("application", "Guide");
      var response = context.render("Home", Props.builder()
          .put("message", "Hello")
          .put("details", Prop.optional(() -> Map.of("enabled", true)))
          .build()).withoutSsr().withHeader("Cache-Control", "private, no-store");
      var outcome = renderer.render(context, response).toCompletableFuture().get();
      System.out.println(outcome.body());
      // Responses and contexts are single-use: create new instances for another request.
      var next = renderer.render(new InertiaContext(request, session, codec),
          new InertiaResponse("Home", Props.empty()).withoutSsr()).toCompletableFuture().get();
      System.out.println(next.body());
    }
  }
}
