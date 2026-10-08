package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.ValidationBridge;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(
    classes = MvcAllErrorsTest.TestApplication.class,
    properties = "inertia.all-errors=true")
@AutoConfigureMockMvc
class MvcAllErrorsTest {
  @Autowired MockMvc mvc;

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({Application.Pages.class, SecurityConfiguration.class, FormController.class})
  static class TestApplication {
    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Users/Index", "Error"));
    }
  }

  record Form(
      @Size(min = 5, message = "At least five characters.")
          @Pattern(regexp = "[A-Z]+", message = "Use uppercase letters.")
          String name) {}

  @Controller
  static class FormController {
    @PostMapping("/multi")
    HttpOutcome submit(
        InertiaContext context, @Valid @RequestBody Form form, BindingResult result) {
      return context.backWithErrors(ValidationBridge.errors(result));
    }
  }

  @Test
  void boundAllErrorsConfigDeliversAllDtoMessagesThroughRedirectAndBag() throws Exception {
    var token = mvc.perform(get("/users")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(token);
    var session = new MockHttpSession();
    mvc.perform(
            post("/multi")
                .session(session)
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .header("Referer", "http://localhost/users")
                .contentType("application/json")
                .content("{\"name\":\"abc\"}"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/users"));
    mvc.perform(
            get("/users")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1")
                .header("X-Inertia-Error-Bag", "signup"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.props.errors.signup.name",
                org.hamcrest.Matchers.containsInAnyOrder(
                    "At least five characters.", "Use uppercase letters.")));
    mvc.perform(
            get("/users")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.props.errors").isEmpty());
  }
}
