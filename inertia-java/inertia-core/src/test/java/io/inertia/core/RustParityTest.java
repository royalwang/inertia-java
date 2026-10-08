package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class RustParityTest {
  @Test
  void completePagesMatchRustExport() throws Exception {
    var codec = new PageCodec();
    try (var input = getClass().getResourceAsStream("/compatibility/pages.json");
        var executor = Executors.newFixedThreadPool(4)) {
      assertNotNull(input);
      var fixtures = codec.read(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      for (var fixture : fixtures.path("cases")) {
        var renderer =
            new ResponseRenderer(
                InertiaConfig.basic("v1", Set.of("Home"))
                    .withAllErrors(fixture.path("allErrors").asBoolean()),
                codec,
                new PropsResolver(codec, executor, Duration.ofSeconds(1), 4));
        var headers = new LinkedHashMap<String, String>();
        headers.put("x-inertia", "true");
        headers.put("x-inertia-version", "v1");
        fixture
            .path("headers")
            .fields()
            .forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
        var props = Props.builder();
        for (var definition : fixture.path("definitions")) {
          var value = definition.get("value");
          Prop prop =
              switch (definition.path("kind").asText()) {
                case "lazy" -> Prop.lazy(() -> value);
                case "optional" -> Prop.optional(() -> value);
                case "deferred" -> Prop.defer(() -> value);
                default -> Prop.value(value);
              };
          props.put(definition.path("key").asText(), prop);
        }
        var request =
            new InertiaRequest("GET", URI.create("https://app.test/users?page=2"), headers);
        var context = new InertiaContext(request, null, codec);
        for (var batch : fixture.path("errors")) context.withErrors(ErrorBags.fromJson(batch));
        var response =
            renderer
                .render(context, new InertiaResponse("Home", props.build()))
                .toCompletableFuture()
                .get();
        assertEquals(
            fixture.path("expectedPage"),
            codec.read(response.body()),
            fixture.path("name").asText());
      }
    }
  }
}
