package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class RustHttpParityTest {
  @TestFactory
  Stream<DynamicTest> actualRustHttpPolicyAndExplicitDifferences() throws Exception {
    var codec = new PageCodec();
    try (var input = getClass().getResourceAsStream("/compatibility/http.json")) {
      assertNotNull(input);
      var fixtures = codec.read(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      assertEquals(45, fixtures.path("cases").size(), "No HTTP fixture silently omitted");
      var names = new HashSet<String>();
      return StreamSupport.stream(fixtures.path("cases").spliterator(), false)
          .map(
              fixture -> {
                String name = fixture.path("name").asText();
                assertTrue(names.add(name), "Unique HTTP fixture name");
                return DynamicTest.dynamicTest(name, () -> compare(fixture));
              });
    }
  }

  private void compare(JsonNode fixture) {
    var codec = new PageCodec();
    var headers = new LinkedHashMap<String, String>();
    fixture
        .path("headers")
        .fields()
        .forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
    var request =
        new InertiaRequest(
            fixture.path("method").asText(), URI.create(fixture.path("url").asText()), headers);
    HttpOutcome outcome =
        switch (fixture.path("operation").asText()) {
          case "before" ->
              ProtocolPolicy.before(request, fixture.path("version").asText()).orElse(null);
          case "redirect" -> ProtocolPolicy.redirect(fixture.path("target").asText());
          case "location" -> ProtocolPolicy.location(request, fixture.path("target").asText());
          case "after" -> {
            var responseHeaders = new LinkedHashMap<String, List<String>>();
            fixture
                .path("responseHeaders")
                .fields()
                .forEachRemaining(
                    e -> {
                      var values = new ArrayList<String>();
                      e.getValue().forEach(value -> values.add(value.asText()));
                      responseHeaders.put(e.getKey(), values);
                    });
            yield ProtocolPolicy.after(
                request,
                new HttpOutcome(
                    fixture.path("status").asInt(),
                    responseHeaders,
                    fixture.path("body").asText()));
          }
          default -> throw new IllegalArgumentException("Unknown HTTP fixture operation");
        };
    JsonNode actual =
        outcome == null
            ? codec.value(null)
            : codec.value(
                Map.of(
                    "status",
                    outcome.status(),
                    "headers",
                    outcome.headers(),
                    "body",
                    outcome.body()));
    JsonNode rust = fixture.get("expectedRust");
    assertNotNull(rust, "Must contain actual Rust output, including null for no before action");
    if (fixture.has("javaExpected")) {
      assertFalse(
          fixture.path("difference").asText().isBlank(), "Every difference needs a stated policy");
      assertNotEquals(
          rust,
          fixture.get("javaExpected"),
          "Stale difference: restore direct parity if policies converge");
      assertEquals(fixture.get("javaExpected"), actual, fixture.path("difference").asText());
    } else {
      assertFalse(fixture.has("difference"), "Unpaired difference annotation");
      assertEquals(rust, actual, "Exact status, all header names/values and body");
    }
  }
}
