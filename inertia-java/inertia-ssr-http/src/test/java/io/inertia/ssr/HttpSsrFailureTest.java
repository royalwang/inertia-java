package io.inertia.ssr;

import static org.junit.jupiter.api.Assertions.*;

import io.inertia.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class HttpSsrFailureTest {
  PageCodec codec = new PageCodec();
  InertiaRequest request =
      new InertiaRequest("GET", URI.create("https://app.test/users"), Map.of());

  Page page() {
    var node = codec.object().put("component", "Users").put("url", "/users").put("version", "v1");
    node.set("props", codec.object());
    return new Page(node);
  }

  HttpSsrGateway gateway(Renderer server, Duration timeout, int limit) {
    return new HttpSsrGateway(server.uri(), Duration.ofSeconds(1), timeout, limit, 1, codec);
  }

  @Test
  void stalledBodyDeadlineClosesExchangeAndNextRequestRecovers() throws Exception {
    try (var server = new Renderer()) {
      var gateway = gateway(server, Duration.ofSeconds(1), 1024);
      var result = gateway.render(page(), request).toCompletableFuture();
      assertTrue(server.entered.await(2, TimeUnit.SECONDS));
      assertEquals(
          "transport-or-timeout",
          assertInstanceOf(SsrGateway.Fallback.class, result.get(3, TimeUnit.SECONDS)).reason());
      assertTrue(server.closed.await(2, TimeUnit.SECONDS), "Timed-out body connection must close");
      server.mode.set("valid");
      assertInstanceOf(
          SsrGateway.Rendered.class,
          gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS));
      assertEquals(2, server.calls.get(), "No render retries");
    }
  }

  @Test
  void overloadedRequestNeverDispatchesAndCallerCancellationRestoresCapacity() throws Exception {
    try (var server = new Renderer()) {
      var gateway = gateway(server, Duration.ofSeconds(5), 1024);
      var pending = gateway.render(page(), request).toCompletableFuture();
      assertTrue(server.entered.await(2, TimeUnit.SECONDS));
      assertEquals(
          "overloaded",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway.render(page(), request).toCompletableFuture().get(1, TimeUnit.SECONDS))
              .reason());
      assertEquals(1, server.calls.get());
      assertTrue(pending.cancel(true));
      assertTrue(server.closed.await(2, TimeUnit.SECONDS), "Cancelled body connection must close");
      server.mode.set("valid");
      assertInstanceOf(
          SsrGateway.Rendered.class,
          gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS));
      assertTrue(pending.isCancelled());
      assertEquals(2, server.calls.get());
    }
  }

  @Test
  void outerRenderCancellationClosesHttpRestoresSessionAndRecoversGateway() throws Exception {
    try (var server = new Renderer();
        var executor = Executors.newSingleThreadExecutor()) {
      var gateway = gateway(server, Duration.ofSeconds(10), 1024);
      var captured = new AtomicReference<Page>();
      var config =
          new InertiaConfig(
              () -> "v1",
              "app",
              Set.of("Users"),
              view -> {
                captured.set(view.page());
                return view.body();
              },
              gateway,
              incoming -> Props.empty(),
              false,
              false);
      var renderer =
          new ResponseRenderer(
              config, codec, new PropsResolver(codec, executor, Duration.ofSeconds(2), 1));
      var session = new MemorySessionStore();
      session.put(InertiaContext.FLASH, codec.value(Map.of("toast", "keep")));
      var pending =
          renderer
              .render(
                  new InertiaContext(request, session, codec),
                  new InertiaResponse("Users", Props.empty()))
              .toCompletableFuture();
      assertTrue(server.entered.await(2, TimeUnit.SECONDS));
      assertTrue(pending.cancel(true));
      assertTrue(
          server.closed.await(2, TimeUnit.SECONDS),
          "Outer future must cancel the real HTTP exchange");
      assertNull(captured.get(), "Cancelled response must not render its root");
      assertEquals("keep", session.get(InertiaContext.FLASH).path("toast").asText());
      server.mode.set("valid");
      var recovered =
          renderer
              .render(
                  new InertiaContext(request, session, codec),
                  new InertiaResponse("Users", Props.empty()))
              .toCompletableFuture()
              .get(2, TimeUnit.SECONDS);
      assertEquals("<div id='app'>OK</div>", recovered.body());
      assertEquals("keep", captured.get().data().at("/flash/toast").asText());
      assertNull(session.get(InertiaContext.FLASH));
      assertEquals(2, server.calls.get(), "No cancellation retry or leaked permit");
    }
  }

  @Test
  void oversizeBeforeEndOfBodyClosesExchangeAndRestoresCapacity() throws Exception {
    try (var server = new Renderer()) {
      server.mode.set("oversize");
      var gateway = gateway(server, Duration.ofSeconds(5), 128);
      assertEquals(
          "transport-or-timeout",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS))
              .reason());
      assertTrue(server.closed.await(2, TimeUnit.SECONDS));
      server.mode.set("valid");
      assertInstanceOf(
          SsrGateway.Rendered.class,
          gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void nonSuccessStatusAndInvalidLimits() throws Exception {
    try (var server = new Renderer()) {
      server.mode.set("status");
      assertEquals(
          "http-status",
          assertInstanceOf(
                  SsrGateway.Fallback.class,
                  gateway(server, Duration.ofSeconds(1), 1024)
                      .render(page(), request)
                      .toCompletableFuture()
                      .get(2, TimeUnit.SECONDS))
              .reason());
      assertThrows(IllegalArgumentException.class, () -> gateway(server, Duration.ZERO, 1024));
      assertThrows(IllegalArgumentException.class, () -> gateway(server, Duration.ofSeconds(1), 0));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new HttpSsrGateway(
                  server.uri(), Duration.ZERO, Duration.ofSeconds(1), 1024, 1, codec));
    }
  }

  @Test
  void synchronousPreparationFailureDoesNotLeakCapacity() throws Exception {
    try (var server = new Renderer()) {
      server.mode.set("valid");
      var gateway = gateway(server, Duration.ofSeconds(1), 1024);
      assertThrows(NullPointerException.class, () -> gateway.render(null, request));
      assertInstanceOf(
          SsrGateway.Rendered.class,
          gateway.render(page(), request).toCompletableFuture().get(2, TimeUnit.SECONDS));
      assertEquals(1, server.calls.get());
    }
  }

  /** A real HTTP/1.1 peer that sends headers and a partial body, then waits for client EOF. */
  static final class Renderer implements AutoCloseable {
    final ServerSocket listener;
    final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    final List<Socket> sockets = new CopyOnWriteArrayList<>();
    final AtomicReference<String> mode = new AtomicReference<>("stall");
    final AtomicInteger calls = new AtomicInteger();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch closed = new CountDownLatch(1);

    Renderer() throws IOException {
      listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
      workers.submit(
          () -> {
            while (!listener.isClosed()) {
              try {
                Socket socket = listener.accept();
                sockets.add(socket);
                workers.submit(() -> serve(socket));
              } catch (IOException stopped) {
                break;
              }
            }
          });
    }

    URI uri() {
      return URI.create("http://127.0.0.1:" + listener.getLocalPort() + "/render");
    }

    void serve(Socket socket) {
      try (socket) {
        socket.setSoTimeout(6000);
        var input = new BufferedInputStream(socket.getInputStream());
        int length = 0;
        String line;
        while (!(line = line(input)).isEmpty()) {
          if (line.toLowerCase(Locale.ROOT).startsWith("content-length:"))
            length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
        }
        input.readNBytes(length);
        calls.incrementAndGet();
        var output = socket.getOutputStream();
        String selected = mode.get();
        if (selected.equals("valid") || selected.equals("status")) {
          byte[] body =
              "{\"head\":[],\"body\":\"<div id='app'>OK</div>\"}".getBytes(StandardCharsets.UTF_8);
          String status = selected.equals("status") ? "503 Unavailable" : "200 OK";
          output.write(
              ("HTTP/1.1 "
                      + status
                      + "\r\nContent-Length: "
                      + body.length
                      + "\r\nConnection: close\r\n\r\n")
                  .getBytes(StandardCharsets.US_ASCII));
          output.write(body);
          output.flush();
          return;
        }
        output.write(
            "HTTP/1.1 200 OK\r\nContent-Length: 4096\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        output.write(
            (selected.equals("oversize") ? "x".repeat(256) : "{").getBytes(StandardCharsets.UTF_8));
        output.flush();
        entered.countDown();
        if (input.read() == -1) closed.countDown();
      } catch (SocketException disconnected) {
        closed.countDown();
      } catch (IOException failure) {
        // A socket timeout is not evidence that the client cancelled.
      }
    }

    static String line(InputStream input) throws IOException {
      var result = new StringBuilder();
      int value;
      while ((value = input.read()) != -1 && value != '\n') result.append((char) value);
      if (value == -1) throw new EOFException();
      return result.toString().trim();
    }

    @Override
    public void close() throws IOException {
      listener.close();
      for (Socket socket : sockets) socket.close();
      workers.shutdownNow();
    }
  }
}
