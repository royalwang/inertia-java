package io.inertia.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.inertia.core.*;
import io.inertia.redis.*;
import io.inertia.spring.*;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class InertiaRedisAutoConfigurationTest {
  @Test
  void selectedObserverReceivesActualDeliveryTransportFailures() throws Exception {
    var events = new java.util.concurrent.CopyOnWriteArrayList<InertiaObserver.Event>();
    try (var listener =
        new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      runner
          .withBean(InertiaObserver.class, () -> events::add)
          .withPropertyValues(
              "inertia.session.store=redis",
              "inertia.session.redis.port=" + listener.getLocalPort(),
              "inertia.session.redis.command-timeout=100ms")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                            context
                                .getBean(InertiaSessionStoreFactory.class)
                                .create(new org.springframework.mock.web.MockHttpServletRequest()))
                    .isInstanceOf(RedisSessionException.class);
                assertThat(events).hasSize(1);
                assertThat(events.getFirst().operation())
                    .isEqualTo(InertiaObserver.Operation.REDIS_READ);
                assertThat(events.getFirst().reason()).isEqualTo(InertiaObserver.Reason.TRANSPORT);
              });
    }
  }

  @Test
  void defaultMetricsObserverReceivesRedisFailures() throws Exception {
    var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    try (var listener =
        new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      runner
          .withConfiguration(AutoConfigurations.of(InertiaMetricsAutoConfiguration.class))
          .withBean(io.micrometer.core.instrument.MeterRegistry.class, () -> registry)
          .withPropertyValues(
              "inertia.session.store=redis",
              "inertia.session.redis.port=" + listener.getLocalPort(),
              "inertia.session.redis.command-timeout=100ms")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(InertiaObserver.class))
                    .isInstanceOf(MicrometerInertiaObserver.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                            context
                                .getBean(InertiaSessionStoreFactory.class)
                                .create(new org.springframework.mock.web.MockHttpServletRequest()))
                    .isInstanceOf(RedisSessionException.class);
                assertThat(
                        registry
                            .get("inertia.redis_read")
                            .tag("reason", "transport")
                            .timer()
                            .count())
                    .isEqualTo(1);
              });
    } finally {
      registry.close();
    }
  }

  final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  InertiaRedisAutoConfiguration.class, InertiaAutoConfiguration.class))
          .withBean(InertiaConfig.class, () -> InertiaConfig.basic("v1", Set.of("Home")));

  @Test
  void defaultAndAbsentOptionalModuleRemainSingleNode() {
    runner
        .withClassLoader(
            new FilteredClassLoader("io.inertia.redis", "org.springframework.data.redis"))
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(InertiaSessionStoreFactory.class);
              assertThat(context).doesNotHaveBean(RedisHttpSessionStoreFactory.class);
              var request = new org.springframework.mock.web.MockHttpServletRequest();
              assertThat(context.getBean(InertiaSessionStoreFactory.class).create(request))
                  .isInstanceOf(HttpSessionStore.class);
            });
  }

  @Test
  void selectedRedisBindsAndRegistersOnlyOneFactoryWithOrderedLifecycleBridge() {
    var backend = mock(RedisSessionBackend.class);
    runner
        .withBean(RedisSessionBackend.class, () -> backend)
        .withPropertyValues(
            "inertia.session.store=redis", "inertia.session.redis.password=not-for-logs")
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(InertiaSessionStoreFactory.class)
                  .hasSingleBean(RedisHttpSessionStoreFactory.class);
              assertThat(context.getBean(InertiaSessionStoreFactory.class))
                  .isSameAs(context.getBean(RedisHttpSessionStoreFactory.class));
              var properties = context.getBean(InertiaRedisProperties.class);
              assertThat(properties.commandTimeout()).isEqualTo(Duration.ofMillis(200));
              assertThat(properties.lease()).isEqualTo(Duration.ofSeconds(30));
              assertThat(properties.toString()).doesNotContain("not-for-logs");
              var registration =
                  (org.springframework.boot.web.servlet.FilterRegistrationBean<?>)
                      context.getBean("inertiaRedisLifecycleFilter");
              assertThat(registration.getOrder()).isEqualTo(Integer.MIN_VALUE + 100);
              assertThat(registration.getFilter()).isInstanceOf(RedisSessionLifecycleFilter.class);
              assertThat(context).hasBean("inertiaRedisSessionListener");
            });
  }

  @Test
  void tlsSelectionCreatesOneOwnedBackendAndPreservesDefaultBeanName() {
    runner
        .withPropertyValues("inertia.session.store=redis", "inertia.session.redis.tls.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(RedisSessionBackend.class);
              assertThat(context).hasBean("inertiaRedisBackend");
              assertThat(context.getBean(InertiaRedisTlsProperties.class).enabled()).isTrue();
            });
  }

  @Test
  void plaintextDefaultCreatesOneOwnedBackend() {
    runner
        .withPropertyValues("inertia.session.store=redis")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(RedisSessionBackend.class);
              assertThat(context.getBean(InertiaRedisTlsProperties.class).enabled()).isFalse();
            });
  }

  @Test
  void tlsTrustMaterialWithoutActivationOrReadableFileFailsAtStartup() {
    for (String[] properties :
        new String[][] {
          {"inertia.session.redis.tls.trust-store=/missing/trust.p12"},
          {
            "inertia.session.redis.tls.enabled=true",
            "inertia.session.redis.tls.trust-store=/missing/trust.p12"
          },
          {
            "inertia.session.redis.tls.enabled=true",
            "inertia.session.redis.tls.trust-store-password=secret-fixture"
          }
        }) {
      runner
          .withPropertyValues("inertia.session.store=redis")
          .withPropertyValues(properties)
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
              });
    }
  }

  @Test
  void applicationBackendBacksOffBothTlsAndPlaintextCreators() {
    var backend = mock(RedisSessionBackend.class);
    runner
        .withBean(RedisSessionBackend.class, () -> backend)
        .withPropertyValues("inertia.session.store=redis", "inertia.session.redis.tls.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(RedisSessionBackend.class);
              assertThat(context.getBean(RedisSessionBackend.class)).isSameAs(backend);
            });
  }

  @Test
  void tlsPropertiesRedactPathsAndCredentials() {
    assertThat(
            new InertiaRedisTlsProperties(true, "/private/trust.p12", "secret-fixture").toString())
        .doesNotContain("/private", "secret-fixture");
  }

  @Test
  void explicitRedisWithoutItsModuleFailsInsteadOfFallingBack() {
    runner
        .withClassLoader(new FilteredClassLoader("io.inertia.redis"))
        .withPropertyValues("inertia.session.store=redis")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void anApplicationFactoryOwnsTheSelectedBackendAndPreventsRedisDefaults() {
    InertiaSessionStoreFactory selected = request -> new MemorySessionStore();
    runner
        .withPropertyValues("inertia.session.store=redis")
        .withBean(InertiaSessionStoreFactory.class, () -> selected)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(InertiaSessionStoreFactory.class);
              assertThat(context.getBean(InertiaSessionStoreFactory.class)).isSameAs(selected);
              assertThat(context).doesNotHaveBean(RedisSessionBackend.class);
            });
  }

  @Test
  void leaseMustExceedTheServletResponseAndStorageBudgets() {
    runner
        .withBean(RedisSessionBackend.class, () -> mock(RedisSessionBackend.class))
        .withPropertyValues("inertia.session.store=redis", "inertia.session.redis.lease=5s")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
  }
}
