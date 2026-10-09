package io.inertia.boot;

import io.inertia.core.*;
import io.inertia.spring.*;
import java.time.Clock;
import java.util.concurrent.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Servlet-only defaults for the Inertia MVC adapter and bounded prop execution.
 *
 * <p>The application supplies {@link InertiaConfig}. Most beans back off when a corresponding
 * application bean exists; the session mutex listener is registered independently. Spring owns the
 * default executor and calls its shutdown method when destroying the bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(InertiaProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InertiaAutoConfiguration {
  /** Creates the configuration instance managed by Spring Boot. */
  public InertiaAutoConfiguration() {}

  /**
   * Creates startup validation for annotated Inertia controller mappings.
   *
   * @param mappings available MVC handler mappings, resolved by the validator
   * @return validator that rejects unsupported handler signatures
   */
  @Bean
  @ConditionalOnMissingBean
  public InertiaHandlerValidator inertiaHandlerValidator(
      ObjectProvider<
              org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping>
          mappings) {
    return new InertiaHandlerValidator(mappings);
  }

  /**
   * Registers the Spring session mutex listener used by session delivery coordination.
   *
   * @return Servlet listener registration for stable per-session mutexes
   */
  @Bean
  public org.springframework.boot.web.servlet.ServletListenerRegistrationBean<
          org.springframework.web.util.HttpSessionMutexListener>
      inertiaSessionMutexListener() {
    return new org.springframework.boot.web.servlet.ServletListenerRegistrationBean<>(
        new org.springframework.web.util.HttpSessionMutexListener());
  }

  /**
   * Creates the default Page JSON codec.
   *
   * @return codec with its own Jackson mapper
   */
  @Bean
  @ConditionalOnMissingBean
  public PageCodec inertiaPageCodec() {
    return new PageCodec();
  }

  /**
   * Creates a Spring-owned bounded executor for synchronous prop providers.
   *
   * <p>Uses platform threads, a bounded queue, and rejection on saturation. Applications replacing
   * the named bean are responsible for its capacity and lifecycle policy.
   *
   * @param properties validated pool sizes and queue capacity
   * @return executor shut down by Spring at bean destruction
   */
  @Bean(name = "inertiaPropsExecutor", destroyMethod = "shutdown")
  @ConditionalOnMissingBean(name = "inertiaPropsExecutor")
  public ExecutorService inertiaPropsExecutor(InertiaProperties properties) {
    return new ThreadPoolExecutor(
        properties.executorCoreSize(),
        properties.executorMaxSize(),
        30,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(properties.executorQueueCapacity()),
        Thread.ofPlatform().name("inertia-prop-", 0).factory(),
        new ThreadPoolExecutor.AbortPolicy());
  }

  /**
   * Creates the request prop resolver using the configured total budget and concurrency.
   *
   * @param codec Page JSON codec
   * @param inertiaPropsExecutor named executor for synchronous providers
   * @param properties validated prop budget and concurrency limit
   * @param observers optional observer; absence selects the no-op observer
   * @return resolver using the UTC clock and application executor
   */
  @Bean
  @ConditionalOnMissingBean
  public PropsResolver inertiaPropsResolver(
      PageCodec codec,
      @Qualifier("inertiaPropsExecutor") ExecutorService inertiaPropsExecutor,
      InertiaProperties properties,
      ObjectProvider<InertiaObserver> observers) {
    return new PropsResolver(
        codec,
        inertiaPropsExecutor,
        properties.propsTimeout(),
        properties.propsConcurrency(),
        Clock.systemUTC(),
        observers.getIfAvailable(() -> InertiaObserver.NOOP));
  }

  /**
   * Creates the renderer, applying the optional validation-presentation override.
   *
   * @param config application render configuration; supplies components, root view, and optional
   *     SSR
   * @param codec Page JSON codec
   * @param resolver prop resolver
   * @param properties optional all-errors override; null retains the application setting
   * @param observers optional observer; absence selects the no-op observer
   * @param environment supplies the diagnostic {@code inertia.ssr-endpoint-id}, defaulting to
   *     renderer
   * @return renderer with the effective configuration; the diagnostic label does not select a URL
   */
  @Bean
  @ConditionalOnMissingBean
  public ResponseRenderer inertiaRenderer(
      InertiaConfig config,
      PageCodec codec,
      PropsResolver resolver,
      InertiaProperties properties,
      ObjectProvider<InertiaObserver> observers,
      Environment environment) {
    return new ResponseRenderer(
        properties.allErrors() == null ? config : config.withAllErrors(properties.allErrors()),
        codec,
        resolver,
        observers.getIfAvailable(() -> InertiaObserver.NOOP),
        environment.getProperty("inertia.ssr-endpoint-id", "renderer"));
  }

  /**
   * Creates MVC argument/return handlers and the exception resolver.
   *
   * @param config application render configuration
   * @param renderer renderer for normal and error Pages
   * @param properties response deadline and isolated session namespace
   * @param errorPages optional application error-page policy
   * @param codec Page JSON codec
   * @return MVC adapter configuration
   */
  @Bean
  @ConditionalOnMissingBean
  public InertiaMvcConfigurer inertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      InertiaProperties properties,
      ObjectProvider<InertiaErrorPage> errorPages,
      PageCodec codec) {
    return new InertiaMvcConfigurer(
        config,
        renderer,
        properties.responseTimeout(),
        errorPages.getIfAvailable(),
        properties.sessionNamespace(),
        codec);
  }
}
