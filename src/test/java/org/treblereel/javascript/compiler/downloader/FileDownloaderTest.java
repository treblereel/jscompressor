/*
 * Copyright © 2025 Treblereel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.treblereel.javascript.compiler.downloader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.config.ServerConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDownloaderTest {

  private Vertx validationVertx;

  @AfterEach
  void closeValidationVertx() throws Exception {
    if (validationVertx != null) {
      await(validationVertx.close());
    }
  }

  @Test
  public void rejectsUnsupportedUrlSchemes() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("file:///etc/passwd"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("ftp://example.com/test.js"));
  }

  @Test
  public void rejectsLoopbackAndPrivateAddresses() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://localhost/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://127.0.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://[::1]/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://10.0.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://172.16.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://192.168.0.1/test.js"));
  }

  @Test
  public void rejectsCloudMetadataAddress() {
    FileDownloader downloader = createDownloader();

    assertThrows(
        IOException.class,
        () -> downloader.downloadFileToTemp("http://169.254.169.254/latest/meta-data/"));
  }

  @Test
  public void rejectsUrlUserInfo() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("https://user@example.com/test.js"));
  }

  @Test
  public void rejectsNonPublicReservedAddresses() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://100.64.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://192.0.2.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://198.18.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://[2001:db8::1]/test.js"));
  }

  @Test
  public void pinsConnectionToValidatedAddress() throws Exception {
    FileDownloader downloader = createDownloader();
    InetAddress address = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});

    RequestOptions options = downloader.createRequestOptions(
        new URI("https://cdn.example.com/scripts/app.js?version=1"), address);

    assertEquals("cdn.example.com", options.getHost());
    assertEquals("8.8.8.8", options.getServer().hostAddress());
    assertEquals(443, options.getServer().port());
    assertEquals("/scripts/app.js?version=1", options.getURI());
    assertTrue(options.isSsl());
    assertFalse(options.getFollowRedirects());
  }

  @Test
  public void downloadsFromValidatedAddressAndEnforcesStreamingLimit() throws Exception {
    Vertx vertx = Vertx.vertx();
    HttpServer server = await(vertx.createHttpServer()
        .requestHandler(request -> {
          String body = request.path().equals("/large") ? "x".repeat(2048) : "const external = true;";
          request.response().end(body);
        })
        .listen(0, "127.0.0.1"));
    FileDownloader downloader = createInitializedDownloader(vertx);
    InetAddress address = InetAddress.getByName("127.0.0.1");

    try {
      FileDownloader.DownloadResponse response = downloader.executeRequest(
          new URI("http://cdn.example.com:" + server.actualPort() + "/small"), address);

      assertEquals(200, response.statusCode());
      assertEquals("const external = true;", response.body());
      assertThrows(
          IOException.class,
          () -> downloader.executeRequest(
              new URI("http://cdn.example.com:" + server.actualPort() + "/large"), address));
    } finally {
      downloader.close();
      await(server.close());
      await(vertx.close());
    }
  }

  @Test
  public void revalidatesRedirectTargets() throws Exception {
    Vertx vertx = Vertx.vertx();
    HttpServer server = await(vertx.createHttpServer()
        .requestHandler(request -> request.response()
            .setStatusCode(302)
            .putHeader("Location", "http://127.0.0.1/internal.js")
            .end())
        .listen(0, "127.0.0.1"));
    InetAddress address = InetAddress.getByName("127.0.0.1");
    FileDownloader downloader = new FileDownloader() {
      @Override
      InetAddress[] resolveAllowedAddresses(URI uri) throws IOException {
        if (uri.getHost().equals("cdn.example.com")) {
          return new InetAddress[]{address};
        }
        return super.resolveAllowedAddresses(uri);
      }
    };
    downloader.serverConfig = new TestServerConfig();
    downloader.vertx = vertx;
    downloader.init();

    try {
      IOException exception = assertThrows(
          IOException.class,
          () -> downloader.downloadFileToTemp(
              "http://cdn.example.com:" + server.actualPort() + "/redirect"));
      assertTrue(exception.getMessage().contains("blocked address"));
    } finally {
      downloader.close();
      await(server.close());
      await(vertx.close());
    }
  }

  @Test
  public void preservesTinyEmptyAndChunkedBodiesAndSourceNames() throws Exception {
    try (Fixture fixture = new Fixture(request -> {
      assertEquals("identity", request.getHeader("Accept-Encoding"));
      if (request.path().equals("/empty")) {
        request.response().end();
      } else if (request.path().equals("/chunked")) {
        request.response().setChunked(true).write("const ");
        request.response().end("external = 'é';");
      } else {
        request.response().end("const external = 'é';");
      }
    })) {
      assertEquals("const external = 'é';", fixture.download("/tiny.js"));
      assertEquals("", fixture.download("/empty"));
      assertEquals("const external = 'é';", fixture.download("/chunked"));
      assertEquals("tiny.js", fixture.downloader.downloadFile(fixture.url("/tiny.js?v=1#fragment")).getName());
      assertEquals("external.js", fixture.downloader.downloadFile(fixture.url("/")).getName());
    }
  }

  @Test
  public void completesBodyBeforeWaitingCallerResumes() throws Exception {
    try (Fixture fixture = new Fixture(request -> request.response().end("FIRST: const external = 'é'; LAST"))) {
      FileDownloader.Download download = fixture.downloader.startDownload(fixture.url("/tiny.js"));
      CountDownLatch completedOnEventLoop = new CountDownLatch(1);
      download.future().onComplete(ignored -> completedOnEventLoop.countDown());
      // Deliberately keep the caller out of the receive path until the entire response
      // has completed. Body handlers must not depend on waking the waiting thread.
      assertTrue(completedOnEventLoop.await(2, TimeUnit.SECONDS));
      assertEquals("FIRST: const external = 'é'; LAST", await(download.future()).body());
    }
  }

  @Test
  public void enforcesActualBytesAndRecoversAfterRejectedResponses() throws Exception {
    try (Fixture fixture = new Fixture(request -> {
      if (request.path().equals("/encoded")) {
        request.response().putHeader("Content-Encoding", "gzip").end("not JavaScript");
      } else {
        request.response().setChunked(true).end("é".repeat(request.path().equals("/large") ? 513 : 512));
      }
    })) {
      assertEquals("é".repeat(512), fixture.download("/boundary"));
      assertTrue(assertThrows(IOException.class, () -> fixture.download("/large")).getMessage().contains("too large"));
      assertTrue(assertThrows(IOException.class, () -> fixture.download("/encoded")).getMessage().contains("Content-Encoding"));
      assertEquals("é".repeat(512), fixture.download("/boundary"));
    }
  }

  @Test
  public void rejectsTruncatedBodyAndCanDownloadAgain() throws Exception {
    try (Fixture fixture = new Fixture(request -> {
      if (request.path().equals("/broken")) {
        request.response().putHeader("Content-Length", "100").write("partial")
            .onComplete(ignored -> request.connection().close());
      } else {
        request.response().end("complete");
      }
    })) {
      assertThrows(IOException.class, () -> fixture.download("/broken"));
      assertEquals("complete", fixture.download("/ok"));
    }
  }

  @Test
  public void handlesRelativeRedirectsAndRejectsInvalidAndExcessiveRedirects() throws Exception {
    try (Fixture fixture = new Fixture(request -> {
      if (request.path().equals("/ok")) {
        request.response().end("complete");
      } else {
        String location = switch (request.path()) {
          case "/redirect" -> "/ok";
          case "/invalid" -> "http://[";
          default -> "/loop";
        };
        request.response().setStatusCode(302).putHeader("Location", location).end();
      }
    })) {
      assertEquals("complete", fixture.download("/redirect"));
      assertTrue(assertThrows(IOException.class, () -> fixture.download("/invalid")).getMessage().contains("Invalid redirect"));
      assertTrue(assertThrows(IOException.class, () -> fixture.download("/loop")).getMessage().contains("Too many redirects"));
    }
  }

  @Test
  public void deadlineStopsContinuousTrafficAndClosesConnection() throws Exception {
    CompletableFuture<Void> closed = new CompletableFuture<>();
    try (Fixture fixture = new Fixture(request -> {
      request.response().setChunked(true).write("x");
      Vertx current = Vertx.currentContext().owner();
      long timer = current.setPeriodic(20, ignored -> request.response().write("x"));
      request.connection().closeHandler(ignored -> {
        current.cancelTimer(timer);
        closed.complete(null);
      });
    })) {
      fixture.downloader.serverConfig = new TestServerConfig() {
        @Override
        public int downloadTimeoutMs() {
          return 300;
        }
      };
      assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
        assertTrue(assertThrows(IOException.class, () -> fixture.download("/slow")).getMessage().contains("deadline"));
        closed.get(2, TimeUnit.SECONDS);
      });
    }
  }

  @Test
  public void redirectDoesNotWaitForOrPropagateErrorsFromDiscardedBody() throws Exception {
    try (Fixture fixture = new Fixture(request -> {
      if (request.path().equals("/ok")) {
        request.response().end("complete");
      } else {
        // Never finish the redirect body: only its headers are relevant.
        request.response().setStatusCode(302).putHeader("Location", "/ok")
            .setChunked(true).write("discarded");
      }
    })) {
      assertEquals("complete", fixture.download("/redirect"));
    }
  }

  @Test
  public void redirectsShareOneDeadline() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    try (Fixture fixture = new Fixture(request -> {
      requests.incrementAndGet();
      Vertx.currentContext().owner().setTimer(100, ignored -> request.response()
          .setStatusCode(302).putHeader("Location", "/again").end());
    })) {
      fixture.downloader.serverConfig = new TestServerConfig() {
        @Override
        public int downloadTimeoutMs() {
          return 250;
        }
      };
      assertTrue(assertThrows(IOException.class, () -> fixture.download("/again")).getMessage().contains("deadline"));
      assertTrue(requests.get() < 4);
    }
  }

  @Test
  public void deadlineIncludesDnsAndDiscardsLateResolution() throws Exception {
    CountDownLatch resolving = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger requests = new AtomicInteger();
    Vertx vertx = Vertx.vertx();
    HttpServer server = await(vertx.createHttpServer().requestHandler(request -> {
      requests.incrementAndGet();
      request.response().end("unexpected");
    }).listen(0, "127.0.0.1"));
    FileDownloader downloader = new FileDownloader() {
      @Override
      InetAddress[] resolveAllowedAddresses(URI uri) throws IOException {
        resolving.countDown();
        try {
          release.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException(e);
        }
        return new InetAddress[]{InetAddress.getLoopbackAddress()};
      }
    };
    downloader.serverConfig = new TestServerConfig() {
      @Override
      public int downloadTimeoutMs() {
        return 300;
      }
    };
    downloader.vertx = vertx;
    downloader.init();
    try {
      assertTrue(assertThrows(IOException.class, () -> downloader.downloadSource(
          "http://fixture.test:" + server.actualPort() + "/")).getMessage().contains("deadline"));
      assertEquals(0, resolving.getCount());
      release.countDown();
      // Drain the worker pool and then its completion callbacks before checking late activity.
      await(vertx.executeBlocking(() -> null));
      CompletableFuture<Void> drained = new CompletableFuture<>();
      vertx.setTimer(100, ignored -> drained.complete(null));
      drained.get(2, TimeUnit.SECONDS);
      assertEquals(0, requests.get());
    } finally {
      release.countDown();
      downloader.close();
      await(server.close());
      await(vertx.close());
    }
  }

  @Test
  public void interruptionCancelsRequestAndPreservesInterruptFlag() throws Exception {
    CountDownLatch received = new CountDownLatch(1);
    CompletableFuture<Void> closed = new CompletableFuture<>();
    try (Fixture fixture = new Fixture(request -> {
      request.connection().closeHandler(ignored -> closed.complete(null));
      received.countDown();
    })) {
      AtomicBoolean interrupted = new AtomicBoolean();
      CompletableFuture<Throwable> failure = new CompletableFuture<>();
      Thread worker = new Thread(() -> {
        try {
          fixture.download("/pending");
          failure.complete(null);
        } catch (Throwable e) {
          interrupted.set(Thread.currentThread().isInterrupted());
          failure.complete(e);
        }
      });
      worker.start();
      try {
        assertTrue(received.await(2, TimeUnit.SECONDS));
        worker.interrupt();
        assertTrue(failure.get(2, TimeUnit.SECONDS) instanceof IOException);
        assertTrue(interrupted.get());
        closed.get(2, TimeUnit.SECONDS);
      } finally {
        worker.interrupt();
        worker.join(2000);
      }
    }
  }

  @Test
  public void rejectsInvalidPortsBeforeConnecting() throws Exception {
    try (Fixture fixture = new Fixture(request -> request.response().end())) {
      assertTrue(assertThrows(IOException.class, () -> fixture.downloader.downloadSource(
          "http://fixture.test:65536/app.js")).getMessage().contains("port"));
      assertTrue(assertThrows(IOException.class, () -> fixture.downloader.downloadSource(
          "http://fixture.test:0/app.js")).getMessage().contains("port"));
    }
  }

  @Test
  public void pinnedHttpsPreservesHostAndRejectsWrongCertificateName() throws Exception {
    Vertx vertx = Vertx.vertx();
    String certificate = "src/test/resources/tls/fixture-cert.pem";
    HttpServer server = await(vertx.createHttpServer(new HttpServerOptions().setSsl(true)
        .setPemKeyCertOptions(new PemKeyCertOptions().setCertPath(certificate)
            .setKeyPath("src/test/resources/tls/fixture-key.pem")))
        .requestHandler(request -> request.response().end(request.getHeader("Host")))
        .listen(0, "127.0.0.1"));
    FileDownloader downloader = new FileDownloader() {
      @Override
      HttpClientOptions clientOptions() {
        return super.clientOptions().setPemTrustOptions(new PemTrustOptions().addCertPath(certificate));
      }
    };
    downloader.vertx = vertx;
    downloader.serverConfig = new TestServerConfig();
    downloader.init();
    try {
      InetAddress pinned = InetAddress.getByName("127.0.0.1");
      assertEquals("fixture.test:" + server.actualPort(), downloader.executeRequest(
          URI.create("https://fixture.test:" + server.actualPort() + "/"), pinned).body());
      IOException exception = assertThrows(IOException.class, () -> downloader.executeRequest(
          URI.create("https://wrong.test:" + server.actualPort() + "/"), pinned));
      assertTrue(exception.getMessage().contains("connect"));
      assertTrue(exception.getCause() != null);
    } finally {
      downloader.close();
      await(server.close());
      await(vertx.close());
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final Vertx vertx = Vertx.vertx();
    private final HttpServer server;
    private final FileDownloader downloader = new FileDownloader() {
      @Override
      InetAddress[] resolveAllowedAddresses(URI uri) throws IOException {
        if (uri.getHost().equals("fixture.test")) {
          return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        }
        return super.resolveAllowedAddresses(uri);
      }
    };

    private Fixture(Handler<HttpServerRequest> handler) throws Exception {
      server = await(vertx.createHttpServer().requestHandler(handler).listen(0, "127.0.0.1"));
      downloader.serverConfig = new TestServerConfig();
      downloader.vertx = vertx;
      downloader.init();
    }

    private String url(String path) {
      return "http://fixture.test:" + server.actualPort() + path;
    }

    private String download(String path) throws IOException {
      return downloader.downloadSource(url(path));
    }

    @Override
    public void close() throws Exception {
      downloader.close();
      await(server.close());
      await(vertx.close());
    }
  }

  private FileDownloader createDownloader() {
    FileDownloader downloader = new FileDownloader();
    downloader.serverConfig = new TestServerConfig();
    validationVertx = Vertx.vertx();
    downloader.vertx = validationVertx;
    downloader.init();
    return downloader;
  }

  private FileDownloader createInitializedDownloader(Vertx vertx) {
    FileDownloader downloader = new FileDownloader();
    downloader.serverConfig = new TestServerConfig();
    downloader.vertx = vertx;
    downloader.init();
    return downloader;
  }

  private static <T> T await(Future<T> future) throws Exception {
    return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  private static class TestServerConfig implements ServerConfig {

    @Override
    public long downloadFileMaxSize() {
      return 1024;
    }

    @Override
    public long requestBodyMaxSize() {
      return 0;
    }

    @Override
    public long cacheMaxSize() {
      return 1024;
    }

    @Override
    public long downloadUrlsPreRequest() {
      return 10;
    }

    @Override
    public int downloadConnectTimeoutMs() {
      return 5000;
    }

    @Override
    public int downloadReadTimeoutMs() {
      return 10000;
    }

    @Override
    public int downloadTimeoutMs() {
      return 5000;
    }

    @Override
    public int downloadMaxRedirects() {
      return 5;
    }

    @Override
    public String cacheLocation() {
      return "cache";
    }

    @Override
    public boolean rateLimitEnabled() {
      return true;
    }

    @Override
    public long rateLimitRequestsPerMinute() {
      return 120;
    }
  }
}
