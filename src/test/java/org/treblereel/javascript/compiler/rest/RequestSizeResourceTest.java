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

package org.treblereel.javascript.compiler.rest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.domain.ErrorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(RequestSizeResourceTest.SmallLimitsProfile.class)
public class RequestSizeResourceTest {

  @TestHTTPResource("/compile")
  URI compileUri;

  @Inject
  Jsonb jsonb;

  @Test
  public void acceptsExactBodyLimitWithAndWithoutContentLength() throws Exception {
    String body = bodyWithPayload(" ".repeat(64));
    assertResponse(body + " ".repeat(512 - body.length()), 200);
  }

  @Test
  public void rejectsBodyOverLimitWithAndWithoutContentLength() throws Exception {
    String body = bodyWithPayload("");
    assertResponse(body + " ".repeat(513 - body.length()), 413);
  }

  @Test
  public void rejectsDecodedPayloadOverLimit() throws Exception {
    assertResponse(bodyWithPayload(" ".repeat(65)), 400);
  }

  @Test
  public void countsUtf8PayloadBytes() throws Exception {
    assertResponse(bodyWithPayload("//" + "я".repeat(31)), 200);
    assertResponse(bodyWithPayload("//" + "я".repeat(32)), 400);
  }

  @Test
  public void acceptsEscapedPayloadAtLimit() throws Exception {
    assertResponse(bodyWithPayload(("\\" + "u0020").repeat(64)), 200);
  }

  private String bodyWithPayload(String payload) {
    return "{\"payload\":\"" + payload + "\",\"outputFileName\":\"default.js\"}";
  }

  private void assertResponse(String body, int expectedStatus) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    try (HttpClient client = HttpClient.newHttpClient()) {
      for (boolean chunked : new boolean[] {false, true}) {
        HttpRequest.BodyPublisher publisher = chunked
            ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes))
            : HttpRequest.BodyPublishers.ofByteArray(bytes);
        HttpRequest request = HttpRequest.newBuilder(compileUri)
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(publisher)
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        if (expectedStatus != 200) {
          assertEquals(expectedStatus, jsonb.fromJson(response.body(), ErrorResponse.class).getStatus());
        }
      }
    }
  }

  public static class SmallLimitsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "server.download-file-max-size", "64",
          "server.request-body-max-size", "512",
          "server.rate-limit-enabled", "false");
    }
  }
}
