package io.inertia.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class RustParityTest {
  @TestFactory
  Stream<DynamicTest> completePagesMatchRustExport() throws Exception {
    var codec = new PageCodec();
    try (var input = getClass().getResourceAsStream("/compatibility/pages.json")) {
      assertNotNull(input);
      var fixtures = codec.read(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      assertEquals(37, fixtures.path("cases").size(), "No fixture silently omitted");
      var names = new HashSet<String>();
      return StreamSupport.stream(fixtures.path("cases").spliterator(), false)
          .map(
              fixture -> {
                String name = fixture.path("name").asText();
                assertTrue(names.add(name), "Unique fixture name");
                return DynamicTest.dynamicTest(name, () -> compare(fixture));
              });
    }
  }

  void compare(JsonNode fixture) throws Exception {
    var codec = new PageCodec();
    try (var executor = Executors.newFixedThreadPool(4)) {
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Home"),
              RootView.minimal(),
              null,
              r -> definitions(fixture.path("configShared")),
              fixture.path("configBigint").asBoolean(),
              fixture.path("configEncrypt").asBoolean(),
              fixture.path("allErrors").asBoolean());
      if (fixture.has("resolvedUrl"))
        config = config.withUrlResolver(r -> fixture.path("resolvedUrl").asText());
      config = config.withSharedPropKeys(fixture.path("exposeShared").asBoolean(true));
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(1), 4));
      var headers = new LinkedHashMap<String, String>();
      headers.put("x-inertia", "true");
      headers.put("x-inertia-version", "v1");
      fixture
          .path("headers")
          .fields()
          .forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
      var request = new InertiaRequest("GET", URI.create("https://app.test/users?page=2"), headers);
      var context = new InertiaContext(request, null, codec);
      for (var batch : fixture.path("errors")) context.withErrors(ErrorBags.fromJson(batch));
      for (var definition : fixture.path("requestShared"))
        context.share(definition.path("key").asText(), prop(definition));
      if (fixture.has("requestEncrypt"))
        context.encryptHistory(fixture.get("requestEncrypt").asBoolean());
      if (fixture.path("preserveFragment").asBoolean()) context.preserveFragment();
      fixture
          .path("requestFlash")
          .fields()
          .forEachRemaining(e -> context.flash(e.getKey(), e.getValue()));
      var page = new InertiaResponse("Home", definitions(fixture.path("definitions")));
      if (fixture.has("responseEncrypt"))
        page.encryptHistory(fixture.get("responseEncrypt").asBoolean());
      if (fixture.has("responseBigint"))
        page.preserveBigIntegers(fixture.get("responseBigint").asBoolean());
      if (fixture.path("clearHistory").asBoolean()) page.clearHistory(true);
      fixture
          .path("responseFlash")
          .fields()
          .forEachRemaining(e -> page.flash(e.getKey(), e.getValue()));
      var response = renderer.render(context, page).toCompletableFuture().get(2, TimeUnit.SECONDS);
      assertEquals(
          fixture.path("expectedPage"), codec.read(response.body()), fixture.path("name").asText());
    }
  }

  static Props definitions(JsonNode definitions) {
    var builder = Props.builder();
    for (var definition : definitions)
      builder.put(definition.path("key").asText(), prop(definition));
    return builder.build();
  }

  static Prop prop(JsonNode definition) {
    var value = definition.get("value");
    Prop prop =
        switch (definition.path("kind").asText()) {
          case "lazy" -> Prop.lazy(() -> value);
          case "optional" -> Prop.optional(() -> value);
          case "deferred" -> Prop.defer(() -> value);
          case "failure" ->
              Prop.lazy(
                  () -> {
                    throw new IllegalStateException("fixture failure");
                  });
          case "nested" -> Prop.value(definitions(definition.path("definitions")));
          case "scroll", "scroll-lazy" -> {
            var metadata = definition.path("scroll");
            var scroll =
                new ScrollPage(
                    value,
                    metadata.path("pageName").asText(),
                    metadata.get("previousPage"),
                    metadata.get("nextPage"),
                    metadata.get("currentPage"),
                    definition.path("wrapper").asText("data"));
            yield definition.path("kind").asText().equals("scroll-lazy")
                ? Prop.scrollWith(() -> scroll)
                : Prop.scroll(scroll);
          }
          case "literal" -> Prop.value(value);
          default -> throw new IllegalArgumentException("Unknown fixture kind");
        };
    if (definition.path("loading").asText().equals("deferred") || definition.has("group")) {
      prop =
          new Prop(
              prop.source(),
              Prop.Loading.DEFERRED,
              definition.path("group").asText("default"),
              prop.always(),
              prop.rescued(),
              prop.mergeOptions(),
              prop.onceOptions(),
              prop.scroll());
    }
    if (definition.path("always").asBoolean())
      prop =
          new Prop(
              prop.source(),
              prop.loading(),
              prop.group(),
              true,
              prop.rescued(),
              prop.mergeOptions(),
              prop.onceOptions(),
              prop.scroll());
    prop =
        switch (definition.path("merge").asText()) {
          case "merge" -> prop.merge();
          case "prepend" -> prop.prepend();
          case "deep" -> prop.deepMerge();
          case "" -> prop;
          default -> throw new IllegalArgumentException("Unknown fixture merge");
        };
    for (var path : definition.path("appendAt")) prop = prop.appendAt(path.asText());
    for (var path : definition.path("prependAt")) prop = prop.prependAt(path.asText());
    for (var path : definition.path("matchOn")) prop = prop.matchOn(path.asText());
    if (definition.path("once").isBoolean() && definition.path("once").asBoolean())
      prop = prop.once();
    if (definition.path("once").isTextual()) prop = prop.onceAs(definition.path("once").asText());
    if (definition.path("fresh").asBoolean()) prop = prop.fresh();
    if (definition.path("rescue").asBoolean()) prop = prop.rescue();
    return prop;
  }
}
