package io.inertia.example;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.PageCodec;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = MvcContractTest.TestApplication.class,
    properties = "inertia.session-namespace=csrf-demo")
@AutoConfigureMockMvc
class BrowserCsrfRecoveryTest {
  @Autowired MockMvc mvc;
  @Autowired PageCodec codec;

  @Test
  void rejectedInertiaWriteReturnsFixedTargetAndOneShotNamespacedError() throws Exception {
    Cookie cookie = mvc.perform(get("/users")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    var session = new MockHttpSession();
    var denial =
        mvc.perform(
                post("/users")
                    .session(session)
                    .cookie(cookie)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1")
                    .header("X-XSRF-TOKEN", "stale-secret")
                    .header("Referer", "https://evil.invalid/")
                    .contentType("application/json")
                    .content("{\"name\":\"Not saved\"}"))
            .andExpect(status().isSeeOther())
            .andExpect(header().string("Location", "/users"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse();
    assertFalse(denial.getContentAsString().contains("stale-secret"));
    var first =
        mvc.perform(
                get("/users")
                    .session(session)
                    .cookie(cookie)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var page = codec.read(first.getContentAsString());
    assertEquals(BrowserCsrfFailureHandler.MESSAGE, page.at("/props/errors/_csrf").asText());
    assertTrue(page.at("/flash/toast").isMissingNode());
    var second =
        mvc.perform(
                get("/users")
                    .session(session)
                    .cookie(cookie)
                    .header("X-Inertia", "true")
                    .header("X-Inertia-Version", "v1"))
            .andReturn()
            .getResponse();
    assertTrue(codec.read(second.getContentAsString()).at("/props/errors/_csrf").isMissingNode());
    mvc.perform(
            post("/users")
                .session(session)
                .cookie(cookie)
                .header("X-Inertia", "true")
                .header("X-XSRF-TOKEN", cookie.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isFound());
  }

  @Test
  void missingCookieRejectsOldHeaderAndIssuesFreshCookieWithoutReplaying() throws Exception {
    var session = new MockHttpSession();
    var denial =
        mvc.perform(
                post("/users")
                    .session(session)
                    .header("X-Inertia", "true")
                    .header("X-XSRF-TOKEN", "old-header")
                    .contentType("application/json")
                    .content("{\"name\":\"Not saved\"}"))
            .andExpect(status().isSeeOther())
            .andReturn()
            .getResponse();
    var fresh = denial.getCookie("XSRF-TOKEN");
    assertNotNull(fresh);
    assertNotEquals("old-header", fresh.getValue());
    mvc.perform(
            post("/users")
                .session(session)
                .cookie(fresh)
                .header("X-Inertia", "true")
                .header("X-XSRF-TOKEN", fresh.getValue())
                .contentType("application/json")
                .content("{\"name\":\"Grace\"}"))
        .andExpect(status().isFound());
  }

  @Test
  void otherDenialsStayForbiddenAndStorageFailureDoesNotRedirect() throws Exception {
    var handler = new BrowserCsrfFailureHandler(codec, "csrf-demo");
    var request = new MockHttpServletRequest("POST", "/users");
    request.addHeader("X-Inertia", "true");
    var response = new MockHttpServletResponse();
    handler.handle(request, response, new AccessDeniedException("secret permission reason"));
    assertEquals(403, response.getStatus());
    assertNull(request.getSession(false));
    var csrf =
        new InvalidCsrfTokenException(
            new DefaultCsrfToken("header", "parameter", "expected"), "actual");
    for (String path : new String[] {"/api/write", "/users/other"}) {
      var other = new MockHttpServletRequest("POST", path);
      other.addHeader("X-Inertia", "true");
      var denied = new MockHttpServletResponse();
      handler.handle(other, denied, csrf);
      assertEquals(403, denied.getStatus());
      assertNull(other.getSession(false));
    }
    var broken = new MockHttpSession();
    broken.setAttribute("io.inertia.session.state.csrf-demo", "incompatible");
    request.setSession(broken);
    response = new MockHttpServletResponse();
    handler.handle(request, response, csrf);
    assertEquals(500, response.getStatus());
    assertNull(response.getHeader("Location"));
    assertFalse(response.getContentAsString().contains("expected"));
  }
}
