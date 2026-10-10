package io.inertia.boot;

import io.inertia.redis.*;
import io.inertia.spring.InertiaSessionStoreFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

/**
 * Opt-in standalone Redis delivery wiring. Does not install host-session replication or change the
 * default starter's single-node behavior. Uses an owned at-most-once transport independent from an
 * application's general-purpose Redis clients and their retry/reconnect policies.
 */
@AutoConfiguration(before = InertiaAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RedisSessionBackend.class)
@ConditionalOnProperty(name = "inertia.session.store", havingValue = "redis")
@ConditionalOnMissingBean(InertiaSessionStoreFactory.class)
@EnableConfigurationProperties({InertiaRedisProperties.class, InertiaProperties.class})
public class InertiaRedisAutoConfiguration {
  /** Creates the configuration instance managed by Spring. */
  public InertiaRedisAutoConfiguration() {}

  /**
   * Creates the dedicated delivery transport, closed at application shutdown.
   *
   * @param properties validated standalone endpoint and capacity
   * @return application-scoped Redis backend
   */
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean(RedisSessionBackend.class)
  public RedisSessionBackend inertiaRedisBackend(InertiaRedisProperties properties) {
    var endpoint = new RedisStandaloneConfiguration(properties.host(), properties.port());
    endpoint.setDatabase(properties.database());
    if (properties.username() != null) endpoint.setUsername(properties.username());
    if (properties.password() != null) endpoint.setPassword(properties.password());
    return new RedisSessionBackend(endpoint, properties.commandTimeout(), properties.maxCommands());
  }

  /**
   * Creates trusted HttpSession/epoch integration.
   *
   * @param backend owned standalone Redis transport
   * @param properties domain capacity and lease policy
   * @param application namespace from the main Inertia configuration
   * @return request factory and host lifecycle listener
   */
  @Bean
  public RedisHttpSessionStoreFactory inertiaRedisSessionStores(
      RedisSessionBackend backend,
      InertiaRedisProperties properties,
      InertiaProperties application) {
    if (properties
            .lease()
            .compareTo(application.responseTimeout().plus(properties.commandTimeout()))
        <= 0)
      throw new IllegalArgumentException(
          "Redis delivery lease must exceed response and storage command budgets");
    return new RedisHttpSessionStoreFactory(
        backend, properties.options(application.sessionNamespace()));
  }

  /**
   * Registers the same factory for Servlet destruction and identity-change events.
   *
   * @param factory trusted request factory
   * @return listener registration owned by Boot's Servlet context
   */
  @Bean
  public ServletListenerRegistrationBean<RedisHttpSessionStoreFactory> inertiaRedisSessionListener(
      RedisHttpSessionStoreFactory factory) {
    return new ServletListenerRegistrationBean<>(factory);
  }

  /**
   * Bridges host invalidation/rotation before Security and MVC, after Spring Session's standard
   * filter.
   *
   * @param factory trusted request factory and revocation coordinator
   * @return ordered synchronous Servlet lifecycle filter
   */
  @Bean
  public org.springframework.boot.web.servlet.FilterRegistrationBean<RedisSessionLifecycleFilter>
      inertiaRedisLifecycleFilter(RedisHttpSessionStoreFactory factory) {
    var registration =
        new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
            new RedisSessionLifecycleFilter(factory));
    registration.setOrder(Integer.MIN_VALUE + 100);
    registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST);
    return registration;
  }
}
