package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import io.inertia.spring.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = DemoAuthTest.App.class,
    properties = {
      "inertia.demo-auth=true",
      "inertia.demo-password=local-demo-test-password",
      "inertia.session-namespace=portal"
    })
@AutoConfigureMockMvc
class DemoAuthTest {
  @Autowired MockMvc mvc;
  @Autowired SelectedStore stores;

  @org.junit.jupiter.api.BeforeEach
  void resetStoreProbe() {
    stores.fail = false;
    stores.calls = 0;
    stores.last = null;
  }

  static class SelectedStore implements InertiaSessionStoreFactory {
    boolean fail;
    int calls;
    jakarta.servlet.http.HttpSession last;

    @Override
    public SessionStore create(jakarta.servlet.http.HttpServletRequest request) {
      calls++;
      last = request.getSession();
      if (fail) throw new IllegalStateException("Delivery unavailable");
      return new HttpSessionStore(last, "portal");
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude =
          org.springframework.boot.autoconfigure.security.servlet
              .UserDetailsServiceAutoConfiguration.class)
  @Import({DemoAuth.class, DemoAuth.Pages.class, SecurityConfiguration.class})
  static class App {
    @Bean
    SelectedStore selectedStore() {
      return new SelectedStore();
    }

    @Bean
    InertiaConfig config() {
      return InertiaConfig.basic("v1", Set.of("Auth/Login", "Auth/Account"));
    }
  }

  @Test
  void anonymousNavigationCannotReachAccountProps() throws Exception {
    mvc.perform(get("/account"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/login"));
    var result =
        mvc.perform(get("/account").header("X-Inertia", "true"))
            .andExpect(status().isConflict())
            .andExpect(header().string("X-Inertia-Location", "/login"))
            .andExpect(content().string(""))
            .andReturn();
    assertNull(result.getRequest().getSession(false));
  }

  @Test
  void loginCreatesCleanSessionAndLogoutNeedsCurrentCsrf() throws Exception {
    var initial = mvc.perform(get("/login")).andReturn();
    var session = (MockHttpSession) initial.getRequest().getSession();
    var id = session.getId();
    new HttpSessionStore(session, "portal")
        .put(InertiaContext.FLASH, new PageCodec().value(Map.of("anonymous", "discard")));
    Cookie before = initial.getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(before);
    int callsBeforeLogin = stores.calls;
    var login =
        mvc.perform(
                post("/login")
                    .session(session)
                    .cookie(before)
                    .header("X-XSRF-TOKEN", before.getValue())
                    .param("username", "demo")
                    .param("password", "local-demo-test-password"))
            .andExpect(status().isSeeOther())
            .andExpect(header().string("Location", "/account"))
            .andReturn();
    assertTrue(stores.calls > callsBeforeLogin, "Success handler must use selected factory");
    assertTrue(session.isInvalid());
    var authenticated = (MockHttpSession) login.getRequest().getSession(false);
    assertNotNull(authenticated);
    assertNotEquals(id, authenticated.getId());
    var account =
        mvc.perform(
                get("/account")
                    .session(authenticated)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andExpect(jsonPath("$.props.username").value("demo"))
            .andExpect(jsonPath("$.clearHistory").value(true))
            .andExpect(jsonPath("$.encryptHistory").value(true))
            .andExpect(jsonPath("$.flash").doesNotExist())
            .andReturn();
    mvc.perform(get("/admin").session(authenticated).header("X-Inertia", "true"))
        .andExpect(status().isForbidden());
    Cookie current = account.getResponse().getCookie("XSRF-TOKEN");
    assertNotNull(current);
    assertNotEquals(before.getValue(), current.getValue());
    mvc.perform(
            post("/logout")
                .session(authenticated)
                .cookie(current)
                .header("X-XSRF-TOKEN", before.getValue())
                .header("X-Inertia", "true"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/account"));
    assertFalse(authenticated.isInvalid());
    mvc.perform(
            post("/logout")
                .session(authenticated)
                .cookie(current)
                .header("X-XSRF-TOKEN", current.getValue())
                .header("X-Inertia", "true"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/login"));
    assertTrue(authenticated.isInvalid());
    mvc.perform(get("/account").header("X-Inertia", "true")).andExpect(status().isConflict());
  }

  @Test
  void rejectedLoginDeliversSafeErrorOnceAndDoesNotAuthenticate() throws Exception {
    var initial = mvc.perform(get("/login")).andReturn();
    var session = (MockHttpSession) initial.getRequest().getSession();
    Cookie token = initial.getResponse().getCookie("XSRF-TOKEN");
    int callsBeforeFailure = stores.calls;
    mvc.perform(
            post("/login")
                .session(session)
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue())
                .param("username", "demo")
                .param("password", "private-wrong-password"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "/login"));
    assertTrue(stores.calls > callsBeforeFailure, "Failure handler must use selected factory");
    var page =
        mvc.perform(
                get("/login")
                    .session(session)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andExpect(
                jsonPath("$.props.errors.credentials")
                    .value("Sign-in failed. Check your credentials and try again."))
            .andExpect(jsonPath("$.props.signedIn").value(false))
            .andReturn();
    assertFalse(page.getResponse().getContentAsString().contains("private-wrong-password"));
    mvc.perform(
            get("/login")
                .session(session)
                .header("X-Inertia", "true")
                .header("X-Inertia-Version", "v1"))
        .andExpect(jsonPath("$.props.errors.credentials").doesNotExist());
    mvc.perform(get("/account").session(session).header("X-Inertia", "true"))
        .andExpect(status().isConflict());
  }

  @Test
  void deliveryFailureAfterAuthenticationDoesNotLeaveAuthenticatedSession() throws Exception {
    var initial = mvc.perform(get("/login")).andReturn();
    var session = (MockHttpSession) initial.getRequest().getSession();
    Cookie token = initial.getResponse().getCookie("XSRF-TOKEN");
    stores.fail = true;
    assertThrows(
        Exception.class,
        () ->
            mvc.perform(
                post("/login")
                    .session(session)
                    .cookie(token)
                    .header("X-XSRF-TOKEN", token.getValue())
                    .param("username", "demo")
                    .param("password", "local-demo-test-password")));
    assertTrue(session.isInvalid());
    assertNotNull(stores.last);
    assertNull(stores.last.getAttribute("SPRING_SECURITY_CONTEXT"));
    stores.fail = false;
    mvc.perform(get("/account").session((MockHttpSession) stores.last).header("X-Inertia", "true"))
        .andExpect(status().isConflict())
        .andExpect(header().string("X-Inertia-Location", "/login"));
  }

  @Test
  void csrfRecoveryUsesSelectedStoreAndStorageFailureDoesNotRedirect() throws Exception {
    var initial = mvc.perform(get("/login")).andReturn();
    var session = (MockHttpSession) initial.getRequest().getSession();
    Cookie token = initial.getResponse().getCookie("XSRF-TOKEN");
    int before = stores.calls;
    mvc.perform(
            post("/login")
                .session(session)
                .cookie(token)
                .header("X-XSRF-TOKEN", "stale")
                .header("X-Inertia", "true"))
        .andExpect(status().isSeeOther());
    assertTrue(stores.calls > before);
    stores.fail = true;
    mvc.perform(
            post("/login")
                .session(session)
                .cookie(token)
                .header("X-XSRF-TOKEN", "stale")
                .header("X-Inertia", "true"))
        .andExpect(status().isInternalServerError())
        .andExpect(header().doesNotExist("Location"));
  }
}
