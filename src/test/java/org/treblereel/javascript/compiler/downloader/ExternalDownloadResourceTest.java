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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@QuarkusTest
@TestProfile(ExternalDownloadResourceTest.Profile.class)
public class ExternalDownloadResourceTest {
  private static Vertx fixtureVertx;
  private static HttpServer server;

  @BeforeAll
  static void startFixture() throws Exception {
    fixtureVertx = Vertx.vertx();
    server = fixtureVertx.createHttpServer().requestHandler(request -> {
      switch (request.path()) {
        case "/ok.js" -> request.response().end("window.externalValue = 'complete';");
        case "/large.js" -> request.response().setChunked(true).end("x".repeat(65));
        default -> request.response().setStatusCode(404).end();
      }
    }).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @AfterAll
  static void closeFixture() throws Exception {
    if (fixtureVertx != null) {
      fixtureVertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void compilesTinyExternalScriptWithEmptyPayload() {
    given().contentType(ContentType.JSON).body(body("/ok.js"))
        .when().post("/compile").then().statusCode(200)
        .body("compiledCode", containsString("complete"));
  }

  @Test
  void returnsJson400ForStreamingLimitAndHttpErrors() {
    given().contentType(ContentType.JSON).body(body("/large.js"))
        .when().post("/compile").then().statusCode(400).contentType(ContentType.JSON)
        .body("status", equalTo(400)).body("error", containsString("too large"));
    given().contentType(ContentType.JSON).body(body("/missing.js"))
        .when().post("/compile").then().statusCode(400).contentType(ContentType.JSON)
        .body("status", equalTo(400)).body("error", containsString("404"));
  }

  private String body(String path) {
    return """
        {"payload":"","outputFileName":"default.js",
         "externalScripts":{"urls":["http://fixture.test:%d%s"]}}
        """.formatted(server.actualPort(), path);
  }

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("server.download-file-max-size", "64", "server.rate-limit-enabled", "false");
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
      return Set.of(FixtureDownloader.class);
    }
  }

  @Alternative
  @ApplicationScoped
  public static class FixtureDownloader extends FileDownloader {
    @Override
    InetAddress[] resolveAllowedAddresses(URI uri) throws IOException {
      if (uri.getHost().equals("fixture.test")) {
        return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
      }
      return super.resolveAllowedAddresses(uri);
    }
  }
}
