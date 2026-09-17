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
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.config.ServerConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileDownloaderTest {

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

  private FileDownloader createDownloader() {
    FileDownloader downloader = new FileDownloader();
    downloader.serverConfig = new TestServerConfig();
    return downloader;
  }

  private FileDownloader createInitializedDownloader(Vertx vertx) {
    FileDownloader downloader = createDownloader();
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
