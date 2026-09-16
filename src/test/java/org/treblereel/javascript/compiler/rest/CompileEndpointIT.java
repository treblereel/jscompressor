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

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;

@QuarkusIntegrationTest
public class CompileEndpointIT {

  @Test
  public void compilesAndDownloadsCode() {
    String downloadId =
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "payload": "function hello(name) { return 'Hello, ' + name; }",
                  "outputFileName": "default.js"
                }
                """)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("compiledCode", notNullValue())
            .body("downloadId", notNullValue())
            .body("warnings", notNullValue())
            .body("errors", empty())
            .body("statistics.originalSize", notNullValue())
            .body("statistics.compiledSize", notNullValue())
            .extract()
            .path("downloadId");

    given()
        .when()
        .get("/compile/{downloadId}", downloadId)
        .then()
        .statusCode(200)
        .contentType(ContentType.BINARY)
        .header("Content-Disposition", containsString("default.js"))
        .body(containsString("Hello"));
  }
}
