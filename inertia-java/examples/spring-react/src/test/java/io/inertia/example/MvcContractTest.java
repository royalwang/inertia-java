package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MvcContractTest.TestApplication.class)
@AutoConfigureMockMvc
class MvcContractTest {
  static final AtomicInteger calls = new AtomicInteger();
  @Autowired MockMvc mvc;

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({Application.Pages.class, Application.Health.class, SecurityConfiguration.class})
  static class TestApplication {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Users/Index", "About", "Error"));
    }

    @Bean
    TestPage page() {
      return new TestPage();
    }
  }

  @org.springframework.stereotype.Controller
  static class TestPage {
    @org.springframework.web.bind.annotation.GetMapping("/counted")
    InertiaResponse counted() {
      calls.incrementAndGet();
      return new InertiaResponse("About", Props.empty());
    }
  }

  @Test
  void firstVisitAndSubsequentJsonAndNotFound() throws Exception {
    mvc.perform(get("/users"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page=\"app\"")));
    mvc.perform(get("/users").header("X-Inertia", "true").header("X-Inertia-Version", "v1"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Inertia", "true"))
        .andExpect(jsonPath("$.component").value("Users/Index"))
        .andExpect(jsonPath("$.deferredProps.dashboard[0]").value("stats"));
    mvc.perform(get("/missing")).andExpect(status().isNotFound());
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(header().doesNotExist("X-Inertia"));
  }

  @Test
  void staleVersionSkipsHandlerAndPartialExecutesDeferred() throws Exception {
    calls.set(0);
    mvc.perform(get("/counted").header("X-Inertia", "true").header("X-Inertia-Version", "old"))
        .andExpect(status().isConflict())
        .andExpect(header().string("X-Inertia-Location", "http://localhost/counted"));
    assertEquals(0, calls.get());
    mvc.perform(
            get("/users")
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1")
                .header("X-Inertia-Partial-Component", "Users/Index")
                .header("X-Inertia-Partial-Data", "stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.props.stats.total").value(2))
        .andExpect(jsonPath("$.props.users").doesNotExist());
  }

  @Test
  void validationRedirectUsesSessionAndIsConsumedOnce() throws Exception {
    var session = new org.springframework.mock.web.MockHttpSession();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users")
                .with(browserCsrf())
                .session(session)
                .header("X-Inertia", "true")
                .header("Referer", "http://localhost/users")
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/users"));
    mvc.perform(
            get("/users")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1")
                .header("X-Inertia-Error-Bag", "createUser"))
        .andExpect(jsonPath("$.props.errors.createUser.name").value("Please enter a name."));
    mvc.perform(
            get("/users")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.props.errors.name").doesNotExist());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users")
                .with(browserCsrf())
                .session(session)
                .contentType("application/json")
                .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isFound());
    mvc.perform(
            get("/users")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.flash.toast").value("Saved Grace (demo only)"));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor browserCsrf()
      throws Exception {
    var token = mvc.perform(get("/users")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(token);
    return request -> {
      request.setCookies(token);
      request.addHeader("X-XSRF-TOKEN", token.getValue());
      return request;
    };
  }

  @Test
  void csrfCookieHeaderIsRequiredForMutation() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users")
                .contentType("application/json")
                .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isForbidden());
    var response = mvc.perform(get("/users")).andExpect(status().isOk()).andReturn().getResponse();
    var token = response.getCookie("XSRF-TOKEN");
    assertNotNull(token);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users")
                .cookie(token)
                .header("X-XSRF-TOKEN", "invalid")
                .contentType("application/json")
                .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users")
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isFound());
  }
}
