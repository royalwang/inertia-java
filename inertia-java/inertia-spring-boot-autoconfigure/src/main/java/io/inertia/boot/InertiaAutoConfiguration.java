package io.inertia.boot;

import io.inertia.core.*;
import io.inertia.spring.InertiaMvcConfigurer;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(InertiaProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InertiaAutoConfiguration {
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
      InertiaProperties properties) {
    return new PropsResolver(
        codec, inertiaPropsExecutor, properties.propsTimeout(), properties.propsConcurrency());
  }

  @Bean
  @ConditionalOnMissingBean
  public ResponseRenderer inertiaRenderer(
      InertiaConfig config, PageCodec codec, PropsResolver resolver) {
    return new ResponseRenderer(config, codec, resolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public InertiaMvcConfigurer inertiaMvcConfigurer(
      InertiaConfig config, ResponseRenderer renderer, InertiaProperties properties) {
    return new InertiaMvcConfigurer(config, renderer, properties.responseTimeout());
  }
}
