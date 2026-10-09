package io.inertia.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.inertia.core.*;
import io.inertia.spring.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class InertiaOverridesTest {
  record Marker(String value) {}

  static class Failure extends RuntimeException {}

  final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(InertiaAutoConfiguration.class, WebMvcAutoConfiguration.class))
          .withBean(InertiaConfig.class, () -> InertiaConfig.basic("v1", Set.of("Home")))
          .withBean(Pages.class, Pages::new);

  static PageCodec customCodec() {
    var module = new SimpleModule();
    module.addSerializer(
        Marker.class,
        new JsonSerializer<Marker>() {
          @Override
          public void serialize(Marker marker, JsonGenerator gen, SerializerProvider provider)
              throws IOException {
            gen.writeString("encoded:" + marker.value());
          }
        });
    return new PageCodec(new ObjectMapper().registerModule(module));
  }

  @Controller
  static class Pages {
    @GetMapping("/codec/page")
    InertiaResponse page(InertiaContext context) {
      context.flash("marker", new Marker("context"));
      return context.render("Home", Props.builder().put("marker", new Marker("prop")).build());
    }

    @GetMapping("/codec/redirect")
    HttpOutcome redirect(InertiaContext context) {
      context.flash("marker", new Marker("redirect"));
      return ProtocolPolicy.redirect("/codec/ok");
    }

    @GetMapping("/codec/ok")
    InertiaResponse ok() {
      return new InertiaResponse("Home", Props.empty());
    }

    @GetMapping("/codec/fail")
    InertiaResponse fail() {
      throw new Failure();
    }

    @ExceptionHandler(Failure.class)
    InertiaResponse advice(InertiaContext context) {
      context.flash("marker", new Marker("advice"));
      return context.render("Home", Props.empty()).status(418);
    }

    @GetMapping("/codec/rest")
    @ResponseBody
    Marker rest() {
      return new Marker("rest");
    }
  }

  @Test
  void customCodecReachesPropsRequestEffectsRedirectsAndAdviceWithoutChangingRest() {
    var codec = customCodec();
    runner
        .withBean(PageCodec.class, () -> codec)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(PageCodec.class);
              assertThat(context.getBean(PageCodec.class)).isSameAs(codec);
              var mvc =
                  MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
              mvc.perform(
                      get("/codec/page")
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "v1"))
                  .andExpect(jsonPath("$.props.marker").value("encoded:prop"))
                  .andExpect(jsonPath("$.flash.marker").value("encoded:context"));
              var redirect =
                  mvc.perform(get("/codec/redirect")).andExpect(status().isFound()).andReturn();
              mvc.perform(
                      get("/codec/ok")
                          .session(
                              (org.springframework.mock.web.MockHttpSession)
                                  redirect.getRequest().getSession())
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "v1"))
                  .andExpect(jsonPath("$.flash.marker").value("encoded:redirect"));
              mvc.perform(
                      get("/codec/fail")
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "v1"))
                  .andExpect(status().is(418))
                  .andExpect(jsonPath("$.flash.marker").value("encoded:advice"));
              mvc.perform(get("/codec/rest"))
                  .andExpect(jsonPath("$.value").value("rest"))
                  .andExpect(header().doesNotExist("X-Inertia"));
            });
  }

  @Test
  void customResolverRendererAndMvcConfigurerBackOffAndServeRequestsOnce() {
    var codec = customCodec();
    var resolver = new PropsResolver(codec, Runnable::run, Duration.ofSeconds(1), 1);
    var config = InertiaConfig.basic("v1", Set.of("Home"));
    var renderer = new ResponseRenderer(config, codec, resolver);
    var mvcConfigurer =
        new InertiaMvcConfigurer(config, renderer, Duration.ofSeconds(2), null, "default", codec);
    runner
        .withBean(PageCodec.class, () -> codec)
        .withBean(PropsResolver.class, () -> resolver)
        .withBean(ResponseRenderer.class, () -> renderer)
        .withBean(InertiaMvcConfigurer.class, () -> mvcConfigurer)
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(PropsResolver.class)
                  .hasSingleBean(ResponseRenderer.class)
                  .hasSingleBean(InertiaMvcConfigurer.class);
              assertThat(context.getBean(PropsResolver.class)).isSameAs(resolver);
              assertThat(context.getBean(ResponseRenderer.class)).isSameAs(renderer);
              assertThat(context.getBean(InertiaMvcConfigurer.class)).isSameAs(mvcConfigurer);
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                  .build()
                  .perform(
                      get("/codec/ok")
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "v1"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.component").value("Home"));
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                  .build()
                  .perform(
                      get("/codec/page")
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "v1"))
                  .andExpect(jsonPath("$.props.marker").value("encoded:prop"))
                  .andExpect(jsonPath("$.flash.marker").value("encoded:context"));
            });
  }

  @Test
  void ambiguousCodecsFailInsteadOfChoosingAnArbitrarySerializer() {
    runner
        .withBean("oneCodec", PageCodec.class, PageCodec::new)
        .withBean("twoCodec", PageCodec.class, InertiaOverridesTest::customCodec)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(
                      org.springframework.beans.factory.NoUniqueBeanDefinitionException.class);
            });
  }

  @Test
  void primaryCodecResolvesMultipleCandidatesConsistently() {
    var codec = customCodec();
    runner
        .withBean("ordinaryCodec", PageCodec.class, PageCodec::new)
        .withBean(
            "primaryCodec", PageCodec.class, () -> codec, definition -> definition.setPrimary(true))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(PageCodec.class)).isSameAs(codec);
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                  .build()
                  .perform(
                      get("/codec/page")
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "v1"))
                  .andExpect(jsonPath("$.props.marker").value("encoded:prop"))
                  .andExpect(jsonPath("$.flash.marker").value("encoded:context"));
            });
  }
}
