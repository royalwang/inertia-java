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

@AutoConfiguration
@EnableConfigurationProperties(InertiaProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InertiaAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public InertiaHandlerValidator inertiaHandlerValidator(
      ObjectProvider<
              org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping>
          mappings) {
    return new InertiaHandlerValidator(mappings);
  }

  @Bean
  public org.springframework.boot.web.servlet.ServletListenerRegistrationBean<
          org.springframework.web.util.HttpSessionMutexListener>
      inertiaSessionMutexListener() {
    return new org.springframework.boot.web.servlet.ServletListenerRegistrationBean<>(
        new org.springframework.web.util.HttpSessionMutexListener());
  }

  @Bean
  @ConditionalOnMissingBean
  public PageCodec inertiaPageCodec() {
    return new PageCodec();
  }

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

  @Bean
  @ConditionalOnMissingBean
  public InertiaMvcConfigurer inertiaMvcConfigurer(
      InertiaConfig config,
      ResponseRenderer renderer,
      InertiaProperties properties,
      ObjectProvider<InertiaErrorPage> errorPages) {
    return new InertiaMvcConfigurer(
        config,
        renderer,
        properties.responseTimeout(),
        errorPages.getIfAvailable(),
        properties.sessionNamespace());
  }
}
