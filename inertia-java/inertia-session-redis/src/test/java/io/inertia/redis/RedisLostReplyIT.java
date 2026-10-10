package io.inertia.redis;

import static io.inertia.redis.RedisSessionStoreIT.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Raw local TCP fault injection, not a replacement Redis protocol implementation. */
class RedisLostReplyIT {
  @org.junit.jupiter.api.BeforeAll
  static void start() throws Exception {
    startRedis();
  }

  @org.junit.jupiter.api.AfterAll
  static void stop() throws Exception {
    stopRedis();
  }

  @Test
  void appliedCasWithLostReplyIsUnknownAndNeverReplayed() throws Exception {
    String key = "inertia-lost-reply-" + java.util.UUID.randomUUID();
    var read = backend.read(key);
    assertEquals(-3, backend.cas(key, read, "not-written", read.now(), 1000)); // cache CAS script
    var field = RedisSessionBackend.class.getDeclaredField("CAS");
    field.setAccessible(true);
    String sha = ((DefaultRedisScript<?>) field.get(null)).getSha1();
    try (var proxy = new ReplyDroppingProxy(port, sha);
        var interrupted =
            new RedisSessionBackend(
                new RedisStandaloneConfiguration("127.0.0.1", proxy.port()),
                Duration.ofMillis(250),
                2)) {
      var fresh = backend.read(key);
      reason(
          RedisSessionException.Reason.UNKNOWN_WRITE,
          () -> interrupted.cas(key, fresh, "committed-without-reply", fresh.now() + 1000, 5000));
      assertEquals(
          "committed-without-reply",
          backend.read(key).json(),
          "Real Redis did execute the mutation");
      assertEquals(1, proxy.mutations.get(), "No mutation replay or reconnect is allowed");
    }
  }

  static final class ReplyDroppingProxy implements AutoCloseable {
    final ServerSocket listener;
    final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    final CopyOnWriteArrayList<Socket> sockets = new CopyOnWriteArrayList<>();
    final AtomicInteger mutations = new AtomicInteger();
    final String marker;

    ReplyDroppingProxy(int upstream, String marker) throws IOException {
      this.marker = marker;
      listener = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
      workers.submit(
          () -> {
            while (!listener.isClosed()) {
              try {
                var client = listener.accept();
                var target = new Socket("127.0.0.1", upstream);
                sockets.add(client);
                sockets.add(target);
                var dropped = new AtomicBoolean();
                workers.submit(() -> forward(client, target, dropped, true));
                workers.submit(() -> forward(target, client, dropped, false));
              } catch (IOException ended) {
                if (!listener.isClosed()) throw new UncheckedIOException(ended);
              }
            }
          });
    }

    int port() {
      return listener.getLocalPort();
    }

    void forward(Socket from, Socket to, AtomicBoolean dropped, boolean request) {
      try {
        var input = from.getInputStream();
        var output = to.getOutputStream();
        byte[] buffer = new byte[8192];
        String carry = "";
        int count;
        while ((count = input.read(buffer)) >= 0) {
          if (request) {
            String text = carry + new String(buffer, 0, count, StandardCharsets.US_ASCII);
            if (text.contains(marker) && dropped.compareAndSet(false, true))
              mutations.incrementAndGet();
            carry = text.substring(Math.max(0, text.length() - marker.length()));
          }
          if (request || !dropped.get()) {
            output.write(buffer, 0, count);
            output.flush();
          }
        }
      } catch (IOException closed) {
        /* owner closes both ends after the timed-out operation */
      }
    }

    @Override
    public void close() throws Exception {
      listener.close();
      for (var socket : sockets) socket.close();
      workers.shutdown();
      assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
