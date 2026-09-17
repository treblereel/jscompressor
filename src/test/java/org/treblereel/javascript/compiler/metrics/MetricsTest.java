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

package org.treblereel.javascript.compiler.metrics;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class MetricsTest {

  @Test
  public void exposesHttpCompilationAndCacheMetrics() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"payload\":\"const value = 1;\",\"outputFileName\":\"metrics.js\"}")
        .when().post("/compile")
        .then().statusCode(200);

    given()
        .accept("text/plain")
        .when().get("/metrics")
        .then().statusCode(200)
        .body(containsString("http_server_requests"))
        .body(containsString("jscompressor_compilation_results_total"))
        .body(containsString("jscompressor_compilation_duration_seconds_count"))
        .body(containsString("jscompressor_cache_usage_bytes"));
  }
}
