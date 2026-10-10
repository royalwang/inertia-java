package io.inertia.boot;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revokes Redis delivery before synchronous host invalidate/changeSessionId operations. Must run
 * after the host-session filter and before authentication/MVC. Uses Servlet wrappers so Spring
 * Session hosts need not emit native HttpSessionIdListener events. Storage uncertainty prevents the
 * host operation and propagates failure; it is not converted into successful logout. External
 * repository deletion/admin revocation must arrange equivalent host lifecycle hooks.
 */
public final class RedisSessionLifecycleFilter extends OncePerRequestFilter {
  private final RedisHttpSessionStoreFactory factory;

  /**
   * Creates a lifecycle bridge for the same factory used by MVC and native host listeners.
   *
   * @param factory application-scoped trusted session factory
   */
  public RedisSessionLifecycleFilter(RedisHttpSessionStoreFactory factory) {
    this.factory = Objects.requireNonNull(factory);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          private final Map<HttpSession, HttpSession> handles = new IdentityHashMap<>();

          @Override
          public HttpSession getSession() {
            return getSession(true);
          }

          @Override
          public HttpSession getSession(boolean create) {
            var session = super.getSession(create);
            return session == null ? null : handles.computeIfAbsent(session, ManagedSession::new);
          }

          @Override
          public String changeSessionId() {
            var session = super.getSession(false);
            if (session != null) factory.beforeHostChange(session);
            return super.changeSessionId();
          }
        },
        response);
  }

  private final class ManagedSession implements HttpSession {
    private final HttpSession delegate;

    ManagedSession(HttpSession delegate) {
      this.delegate = delegate;
    }

    @Override
    public long getCreationTime() {
      return delegate.getCreationTime();
    }

    @Override
    public String getId() {
      return delegate.getId();
    }

    @Override
    public long getLastAccessedTime() {
      return delegate.getLastAccessedTime();
    }

    @Override
    public ServletContext getServletContext() {
      return delegate.getServletContext();
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
      delegate.setMaxInactiveInterval(interval);
    }

    @Override
    public int getMaxInactiveInterval() {
      return delegate.getMaxInactiveInterval();
    }

    @Override
    public Object getAttribute(String name) {
      return delegate.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
      return delegate.getAttributeNames();
    }

    @Override
    public void setAttribute(String name, Object value) {
      delegate.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
      delegate.removeAttribute(name);
    }

    @Override
    public boolean isNew() {
      return delegate.isNew();
    }

    @Override
    public void invalidate() {
      factory.beforeHostChange(delegate);
      delegate.invalidate();
    }
  }
}
