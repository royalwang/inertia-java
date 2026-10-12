package io.inertia.redis;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.PageCodec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Actual owned TLS Redis and a bounded TLS reply-loss fixture; no mock backend. */
class RedisTlsIT {
  static Path work;
  static Process redis;
  static int port;
  static String password;
  static final char[] storePassword = "local-fixture-store".toCharArray();

  static void command(String... args) throws Exception {
    var process =
        new ProcessBuilder(args)
            .directory(work.toFile())
            .redirectErrorStream(true)
            .redirectOutput(
                ProcessBuilder.Redirect.appendTo(work.resolve("certificate-tools.log").toFile()))
            .start();
    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Certificate command deadline");
    assertEquals(0, process.exitValue(), "Certificate fixture generation failed");
  }

  @BeforeAll
  static void start() throws Exception {
    String executable = System.getProperty("inertia.redis.tls.server");
    assertNotNull(executable, "A real TLS-enabled Redis executable is required");
    work = Files.createTempDirectory("inertia-redis-tls-");
    password = UUID.randomUUID().toString();
    command(
        "openssl",
        "req",
        "-x509",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        "ca.key",
        "-out",
        "ca.crt",
        "-days",
        "2",
        "-subj",
        "/CN=Inertia local test CA");
    command(
        "openssl",
        "req",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        "server.key",
        "-out",
        "server.csr",
        "-subj",
        "/CN=localhost");
    Files.writeString(
        work.resolve("server.ext"), "subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\n");
    command(
        "openssl",
        "x509",
        "-req",
        "-in",
        "server.csr",
        "-CA",
        "ca.crt",
        "-CAkey",
        "ca.key",
        "-CAcreateserial",
        "-out",
        "server.crt",
        "-days",
        "2",
        "-extfile",
        "server.ext");
    command(
        "openssl",
        "pkcs12",
        "-export",
        "-inkey",
        "server.key",
        "-in",
        "server.crt",
        "-certfile",
        "ca.crt",
        "-out",
        "server.p12",
        "-passout",
        "pass:local-fixture-store");
    command(
        Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
        "-importcert",
        "-noprompt",
        "-storetype",
        "PKCS12",
        "-alias",
        "ca",
        "-file",
        "ca.crt",
        "-keystore",
        "trust.p12",
        "-storepass",
        "local-fixture-store");
    command(
        "openssl",
        "req",
        "-x509",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        "wrong.key",
        "-out",
        "wrong.crt",
        "-days",
        "2",
        "-subj",
        "/CN=Wrong local CA");
    command(
        Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
        "-importcert",
        "-noprompt",
        "-storetype",
        "PKCS12",
        "-alias",
        "wrong",
        "-file",
        "wrong.crt",
        "-keystore",
        "wrong.p12",
        "-storepass",
        "local-fixture-store");
    try (var listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      port = listener.getLocalPort();
    }
    var config = work.resolve("redis.conf");
    Files.writeString(
        config,
        "bind 127.0.0.1\nport 0\ntls-port "
            + port
            + "\ntls-cert-file "
            + work.resolve("server.crt")
            + "\ntls-key-file "
            + work.resolve("server.key")
            + "\ntls-ca-cert-file "
            + work.resolve("ca.crt")
            + "\ntls-auth-clients no\nrequirepass "
            + password
            + "\nsave \"\"\nappendonly no\n");
    Files.setPosixFilePermissions(
        config,
        Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
    redis =
        new ProcessBuilder(executable, config.toString())
            .directory(work.toFile())
            .redirectErrorStream(true)
            .redirectOutput(work.resolve("redis.log").toFile())
            .start();
    long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!Files.readString(work.resolve("redis.log")).contains("Ready to accept connections")) {
      assertTrue(redis.isAlive(), "TLS Redis exited; inspect owned runtime log");
      if (System.nanoTime() > end) fail("TLS Redis startup deadline");
      Thread.sleep(20);
    }
    System.out.println("TLS qualification fixture: " + work);
  }

  @AfterAll
  static void stop() throws Exception {
    if (redis != null) {
      redis.destroy();
      assertTrue(redis.waitFor(5, TimeUnit.SECONDS));
    }
  }

  static RedisStandaloneConfiguration endpoint(String host, String secret, int targetPort) {
    var config = new RedisStandaloneConfiguration(host, targetPort);
    config.setPassword(secret);
    return config;
  }

  static RedisTlsOptions trust(String file, char[] secret) {
    return RedisTlsOptions.trustStore(work.resolve(file), secret);
  }

  static RedisSessionBackend backend(String host, String secret, RedisTlsOptions tls) {
    return new RedisSessionBackend(endpoint(host, secret, port), Duration.ofSeconds(2), 4, tls);
  }

  static void denied(RedisSessionBackend backend) {
    try (backend) {
      var failure =
          assertThrows(
              RedisSessionException.class, () -> backend.read("tls-negative-" + UUID.randomUUID()));
      assertEquals(RedisSessionException.Reason.READ_FAILED, failure.reason());
      assertFalse(failure.toString().contains(password));
    }
  }

  @Test
  void trustedTlsAndDefensivePasswordCopyDeliverRealState() {
    char[] copied = storePassword.clone();
    var tls = trust("trust.p12", copied);
    Arrays.fill(copied, 'x');
    try (var backend = backend("localhost", password, tls)) {
      var store =
          new RedisSessionStore(
              backend,
              RedisSessionOptions.defaults("tls-test"),
              UUID.randomUUID().toString(),
              null);
      store.put("notice", new PageCodec().value("TLS delivered"));
      var delivery = store.beginPageDelivery();
      assertEquals("TLS delivered", delivery.data().path("notice").asText());
      store.completePageDelivery(delivery);
      assertNull(store.get("notice"));
      assertFalse(tls.toString().contains("local-fixture-store"));
      assertFalse(tls.toString().contains(work.toString()));
    }
  }

  @Test
  void systemTrustUsesTheConfiguredJdkAnchorsInAnIsolatedJvm() throws Exception {
    var child =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-Djavax.net.ssl.trustStore=" + work.resolve("trust.p12"),
            "-Djavax.net.ssl.trustStoreType=PKCS12",
            "-Djavax.net.ssl.trustStorePassword=local-fixture-store",
            "-cp",
            System.getProperty("java.class.path"),
            RedisTlsIT.class.getName(),
            Integer.toString(port));
    child.environment().put("INERTIA_TLS_FIXTURE_PASSWORD", password);
    var process =
        child
            .redirectErrorStream(true)
            .redirectOutput(work.resolve("system-trust.log").toFile())
            .start();
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "System trust child deadline");
      assertEquals(
          0, process.exitValue(), "Configured JDK trust must connect to the actual TLS Redis");
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
      }
    }
  }

  public static void main(String[] args) {
    port = Integer.parseInt(args[0]);
    password = System.getenv("INERTIA_TLS_FIXTURE_PASSWORD");
    try (var backend = backend("localhost", password, RedisTlsOptions.systemTrust())) {
      if (backend.read("system-trust-" + UUID.randomUUID()).json() == null)
        throw new AssertionError("TLS read did not return a result");
    }
  }

  @Test
  void systemTrustRejectsThePrivateFixtureCa() {
    denied(backend("localhost", password, RedisTlsOptions.systemTrust()));
  }

  @Test
  void absentTrustFileIsRejectedWithoutLeakingItsPath() {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RedisTlsOptions.trustStore(
                    work.resolve("missing-private-trust.p12"), storePassword));
    assertFalse(failure.toString().contains(work.toString()));
  }

  @Test
  void wrongTrustChainFails() {
    denied(backend("localhost", password, trust("wrong.p12", storePassword)));
  }

  @Test
  void hostnameMismatchFails() {
    denied(backend("127.0.0.1", password, trust("trust.p12", storePassword)));
  }

  @Test
  void incorrectAuthenticationFails() {
    denied(backend("localhost", "wrong-password", trust("trust.p12", storePassword)));
  }

  @Test
  void incorrectTrustStorePasswordFails() {
    denied(
        backend("localhost", password, trust("trust.p12", "wrong-store-password".toCharArray())));
  }

  @Test
  void plaintextDoesNotSilentlyNegotiateTls() {
    denied(
        new RedisSessionBackend(endpoint("localhost", password, port), Duration.ofMillis(300), 1));
  }

  @Test
  void executedTlsCasWithLostReplyIsUnknownAndSubmittedOnce() throws Exception {
    String key = "tls-lost-" + UUID.randomUUID();
    try (var direct = backend("localhost", password, trust("trust.p12", storePassword))) {
      var initial = direct.read(key);
      assertEquals(-3, direct.cas(key, initial, "not-written", initial.now(), 1000));
      var field = RedisSessionBackend.class.getDeclaredField("CAS");
      field.setAccessible(true);
      String marker = ((DefaultRedisScript<?>) field.get(null)).getSha1();
      try (var proxy = new TlsReplyProxy(marker);
          var interrupted =
              new RedisSessionBackend(
                  endpoint("localhost", password, proxy.port()),
                  Duration.ofSeconds(2),
                  2,
                  trust("trust.p12", storePassword))) {
        var before = direct.read(key);
        var failure =
            assertThrows(
                RedisSessionException.class,
                () ->
                    interrupted.cas(
                        key, before, "executed-on-real-TLS-Redis", before.now() + 5000, 10000));
        assertEquals(RedisSessionException.Reason.UNKNOWN_WRITE, failure.reason());
        assertEquals("executed-on-real-TLS-Redis", direct.read(key).json());
        assertEquals(1, proxy.mutations.get());
        // A new explicit operation may reconnect; the unknown mutation was never replayed.
        assertEquals("executed-on-real-TLS-Redis", interrupted.read(key).json());
        assertEquals(1, proxy.mutations.get());
      }
    }
  }

  static SSLContext context(boolean server) throws Exception {
    var store = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(work.resolve(server ? "server.p12" : "trust.p12"))) {
      store.load(in, storePassword);
    }
    var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tm.init(store);
    var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(store, storePassword);
    var context = SSLContext.getInstance("TLS");
    context.init(server ? km.getKeyManagers() : null, tm.getTrustManagers(), null);
    return context;
  }

  static final class TlsReplyProxy implements AutoCloseable {
    final SSLServerSocket listener;
    final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    final List<Socket> sockets = new CopyOnWriteArrayList<>();
    final AtomicInteger mutations = new AtomicInteger();
    final SSLContext upstream;

    TlsReplyProxy(String marker) throws Exception {
      upstream = context(false);
      listener =
          (SSLServerSocket)
              context(true)
                  .getServerSocketFactory()
                  .createServerSocket(0, 4, InetAddress.getLoopbackAddress());
      workers.submit(
          () -> {
            while (!listener.isClosed()) {
              try {
                var client = (SSLSocket) listener.accept();
                sockets.add(client);
                workers.submit(() -> relay(client, marker));
              } catch (IOException closed) {
                if (!listener.isClosed()) throw new UncheckedIOException(closed);
              }
            }
          });
    }

    int port() {
      return listener.getLocalPort();
    }

    void relay(SSLSocket client, String marker) {
      try (client;
          var target =
              (SSLSocket) upstream.getSocketFactory().createSocket("localhost", RedisTlsIT.port)) {
        sockets.add(target);
        client.setSoTimeout(5000);
        target.setSoTimeout(5000);
        var parameters = target.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        target.setSSLParameters(parameters);
        client.startHandshake();
        target.startHandshake();
        while (true) {
          byte[] request = frame(client.getInputStream());
          target.getOutputStream().write(request);
          target.getOutputStream().flush();
          byte[] reply = frame(target.getInputStream());
          if (new String(request, StandardCharsets.US_ASCII).contains(marker)) {
            assertEquals(":1\r\n", new String(reply, StandardCharsets.US_ASCII));
            mutations.incrementAndGet();
            return; // actual Redis completed CAS; suppress the reply
          }
          client.getOutputStream().write(reply);
          client.getOutputStream().flush();
        }
      } catch (IOException ended) {
        /* owned client closes dedicated connections after commands */
      }
    }

    @Override
    public void close() throws Exception {
      listener.close();
      for (var socket : sockets) socket.close();
      workers.shutdown();
      assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  // Bounded RESP frame pass-through used only to drop one real server reply, including HELLO RESP3.
  static byte[] frame(InputStream in) throws IOException {
    var out = new ByteArrayOutputStream();
    int type = in.read();
    if (type < 0) throw new EOFException();
    out.write(type);
    var line = new ByteArrayOutputStream();
    int c;
    while ((c = in.read()) >= 0) {
      out.write(c);
      line.write(c);
      if (c == '\n') break;
      if (line.size() > 1024 * 1024) throw new IOException("Frame line bound");
    }
    if (c < 0) throw new EOFException();
    String text = line.toString(StandardCharsets.US_ASCII).trim();
    if (type == '$' || type == '!' || type == '=') {
      int count = Integer.parseInt(text);
      if (count > 1024 * 1024) throw new IOException("Frame bound");
      if (count >= 0) {
        byte[] bytes = in.readNBytes(count + 2);
        if (bytes.length != count + 2) throw new EOFException();
        out.write(bytes);
      }
    } else if (type == '*' || type == '%' || type == '~' || type == '>' || type == '|') {
      int count = Integer.parseInt(text);
      if (count > 4096) throw new IOException("Frame item bound");
      if (type == '%' || type == '|') count *= 2;
      for (int i = 0; i < count; i++) out.write(frame(in));
    }
    return out.toByteArray();
  }
}
