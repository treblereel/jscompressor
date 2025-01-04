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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.domain.CompileRequest;
import org.treblereel.javascript.compiler.domain.ExternalScripts;
import org.treblereel.javascript.compiler.domain.Formatting;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class CompilerResourceTest {

  @Inject
  Jsonb jsonb;

  @Test
  public void testCompileSuccessWhitespaceOnly() {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello(name) {\n    alert('Hello, ' + name);\n}\nhello('New user');");
    compileRequest.setCompilationLevel("Whitespace only");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());

    String json = jsonb.toJson(compileRequest);

    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", is("'use strict';function hello(name){alert(\"Hello, \"+name)}hello(\"New user\");"))
            .body("downloadId", notNullValue())
            .body("errors", hasSize(0))
            .body("statistics.compiledSize", is(74))
            .body("statistics.originalSize", is(72))
            .body("warnings", hasSize(0));

    String hash = Long.toHexString(compileRequest.hashCode());
    given()
            .contentType(ContentType.TEXT)
            .body(hash)
            .get("/compile/" + hash)
            .then()
            .statusCode(200)
            .body(is("'use strict';function hello(name){alert(\"Hello, \"+name)}hello(\"New user\");"));
  }

  @Test
  public void testCompileSuccessSimple() {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello(name) {\n    alert('Hello, ' + name);\n}\nhello('New user');");
    compileRequest.setCompilationLevel("Simple");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());

    String json = jsonb.toJson(compileRequest);

    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", is("'use strict';function hello(a){alert(\"Hello, \"+a)}hello(\"New user\");"))
            .body("downloadId", notNullValue())
            .body("errors", hasSize(0))
            .body("statistics.compiledSize", is(68))
            .body("statistics.originalSize", is(72))
            .body("warnings", hasSize(0));
  }

  @Test
  public void testCompileSuccessAdvanced() {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello(name) {\n    alert('Hello, ' + name);\n}\nhello('New user');");
    compileRequest.setCompilationLevel("Advanced");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());

    String json = jsonb.toJson(compileRequest);

    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", is("'use strict';alert(\"Hello, New user\");"))
            .body("downloadId", notNullValue())
            .body("errors", hasSize(0))
            .body("statistics.compiledSize", is(38))
            .body("statistics.originalSize", is(72))
            .body("warnings", hasSize(0));
  }

  @Test
  public void testCompileSuccessAdvinced() {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello(name) {\n    alert('Hello, ' + name);\n}\nhello('New user');");
    compileRequest.setCompilationLevel("Advanced");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());

    String json = jsonb.toJson(compileRequest);

    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", is("'use strict';alert(\"Hello, New user\");"))
            .body("downloadId", notNullValue())
            .body("errors", hasSize(0))
            .body("statistics.compiledSize", is(38))
            .body("statistics.originalSize", is(72))
            .body("warnings", hasSize(0));
  }


  @Test
  public void testBulkhead() throws InterruptedException {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello(name) {\n    alert('Hello, ' + name);\n}\nhello('New user');");
    compileRequest.setCompilationLevel("Advanced");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());

    String json = jsonb.toJson(compileRequest);


    int totalRequests = 15;
    ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
    CountDownLatch latch = new CountDownLatch(totalRequests);
    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger bulkheadRejectedCount = new AtomicInteger();

    for (int i = 0; i < totalRequests; i++) {
      executor.submit(() -> {
        try {
          io.restassured.response.Response response = given()
                  .contentType(ContentType.JSON)
                  .body(json)
                  .when()
                  .post("/compile")
                  .then()
                  .extract()
                  .response();

          int statusCode = response.getStatusCode();
          if (statusCode == 200) {
            successCount.incrementAndGet();
          } else if (statusCode == 429) {
            bulkheadRejectedCount.incrementAndGet();
          } else {
            System.out.println("Unexpected status code: " + statusCode);
          }
        } catch (Exception e) {
          // ignore
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    assertTrue(successCount.get() <= 10, "No more than 10 requests should be accepted");
    assertTrue(bulkheadRejectedCount.get() >= 5, "No less than 5 requests should be rejected");
  }

}
