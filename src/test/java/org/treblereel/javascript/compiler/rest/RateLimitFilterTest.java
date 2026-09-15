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

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@TestProfile(RateLimitFilterTest.LimitedProfile.class)
public class RateLimitFilterTest {

  @Test
  public void enforcesSharedLimitForCompileAndDownloadOnly() {
    given()
        .when().get("/openapi.yaml")
        .then().statusCode(200);

    given()
        .contentType(ContentType.JSON)
        .body("{\"payload\":\"alert(1);\",\"outputFileName\":\"default.js\"}")
        .when().post("/compile")
        .then().statusCode(200);

    given()
        .contentType(ContentType.JSON)
        .body("{\"payload\":\"alert(1);\",\"outputFileName\":\"default.js\"}")
        .when().post("/compile")
        .then().statusCode(429)
        .contentType(ContentType.JSON)
        .body("status", is(429))
        .body("error", is("Rate limit exceeded. Please try again later."));

    given()
        .when().get("/compile/missing")
        .then().statusCode(429)
        .contentType(ContentType.JSON)
        .body("status", is(429))
        .body("error", is("Rate limit exceeded. Please try again later."));

    given()
        .when().get("/openapi.yaml")
        .then().statusCode(200);
  }

  public static class LimitedProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "server.rate-limit-enabled", "true",
          "server.rate-limit-requests-per-minute", "1");
    }
  }
}
