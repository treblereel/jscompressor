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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.validation.Validator;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.domain.CompileRequest;
import org.treblereel.javascript.compiler.domain.ExternalScripts;
import org.treblereel.javascript.compiler.domain.Formatting;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class CompilerResourceTest {

  @Inject
  Jsonb jsonb;

  @Inject
  Validator validator;

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
  public void testCompileCorsPreflight() {
    String origin = "https://test.reporthost.com";

    given()
        .header("Origin", origin)
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "Content-Type")
        .when()
        .options("/compile")
        .then()
        .statusCode(200)
        .header("Access-Control-Allow-Origin", is(origin))
        .header("Access-Control-Allow-Methods", containsString("POST"))
        .header("Access-Control-Allow-Headers", containsString("Content-Type"));
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
  public void testCompileRejectsPrivateExternalScriptUrl() {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello(name) {\n    alert('Hello, ' + name);\n}\nhello('New user');");
    compileRequest.setCompilationLevel("Simple");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    ExternalScripts externalScripts = new ExternalScripts();
    externalScripts.setUrls(List.of("http://127.0.0.1/test.js"));
    compileRequest.setExternalScripts(externalScripts);

    String json = jsonb.toJson(compileRequest);

    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("status", is(400))
            .body("error", containsString("blocked address"));
  }

  @Test
  public void testValidationAcceptsDocumentedCompilationLevels() {
    assertTrue(validator.validate(createCompileRequest("WHITESPACE")).isEmpty());
    assertTrue(validator.validate(createCompileRequest("SIMPLE")).isEmpty());
    assertTrue(validator.validate(createCompileRequest("ADVANCED")).isEmpty());
  }

  @Test
  public void testValidationAcceptsUiCompilationLevels() {
    assertTrue(validator.validate(createCompileRequest("Whitespace only")).isEmpty());
    assertTrue(validator.validate(createCompileRequest("Simple")).isEmpty());
    assertTrue(validator.validate(createCompileRequest("Advanced")).isEmpty());
  }

  @Test
  public void testValidationAcceptsOptionalDefaults() {
    CompileRequest compileRequest = createCompileRequest("Simple");
    compileRequest.setCompilationLevel(null);
    compileRequest.setWarningLevel(null);
    compileRequest.setFormatting(null);
    compileRequest.setExternalScripts(null);

    assertTrue(validator.validate(compileRequest).isEmpty());
  }

  @Test
  public void testCompileUsesDefaultCompilationLevelWhenMissing() {
    given()
            .contentType(ContentType.JSON)
            .body("""
            {
              "payload": "function hello() {}",
              "warningLevel": "DEFAULT",
              "outputFileName": "default.js",
              "externalScripts": {"urls": []}
            }
            """)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", notNullValue());
  }

  @Test
  public void testCompileUsesDefaultWarningLevelWhenMissing() {
    given()
            .contentType(ContentType.JSON)
            .body("""
            {
              "payload": "function hello() {}",
              "compilationLevel": "Simple",
              "outputFileName": "default.js",
              "externalScripts": {"urls": []}
            }
            """)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", notNullValue());
  }

  @Test
  public void testValidationAcceptsNullExternalScriptUrls() {
    CompileRequest compileRequest = createCompileRequest("Simple");
    ExternalScripts externalScripts = new ExternalScripts();
    externalScripts.setUrls(null);
    compileRequest.setExternalScripts(externalScripts);

    assertTrue(validator.validate(compileRequest).isEmpty());
  }

  @Test
  public void testCompileRejectsMissingPayload() {
    assertCompileBadRequest(
            """
            {
              "compilationLevel": "Simple",
              "warningLevel": "DEFAULT",
              "outputFileName": "default.js",
              "externalScripts": {"urls": []}
            }
            """);
  }

  @Test
  public void testCompileRejectsMalformedJson() {
    assertCompileBadRequest("{");
  }

  @Test
  public void testCompileRejectsNullRequest() {
    assertCompileBadRequest("null");
  }

  @Test
  public void testCompileRejectsEmptyLanguageIn() {
    assertCompileBadRequest("""
        {"payload":"alert(1);","outputFileName":"default.js","language":{"languageIn":""}}
        """);
  }

  @Test
  public void testCompileRejectsEmptyLanguageOut() {
    assertCompileBadRequest("""
        {"payload":"alert(1);","outputFileName":"default.js","language":{"languageOut":""}}
        """);
  }

  @Test
  public void testCompileAcceptsMissingAndNullOptionalFields() {
    for (String json : List.of(
        """
        {"payload":"alert(1);","outputFileName":"default.js"}
        """,
        """
        {"payload":"alert(1);","outputFileName":"default.js",
         "compilationLevel":null,"warningLevel":null,"formatting":null,
         "externalScripts":null,"language":null}
        """,
        """
        {"payload":"alert(1);","outputFileName":"default.js","language":{}}
        """,
        """
        {"payload":"alert(1);","outputFileName":"default.js",
         "language":{"languageIn":null,"languageOut":null}}
        """)) {
      given()
          .contentType(ContentType.JSON)
          .body(json)
          .when().post("/compile")
          .then().statusCode(200)
          .body("compiledCode", notNullValue())
          .body("errors", hasSize(0));
    }
  }

  @Test
  public void testCompileRejectsBlankExternalScriptUrl() {
    assertCompileBadRequest(
            """
            {
              "payload": "function hello() {}",
              "compilationLevel": "Simple",
              "warningLevel": "DEFAULT",
              "outputFileName": "default.js",
              "externalScripts": {"urls": [" "]}
            }
            """);
  }

  @Test
  public void testCompileRejectsInvalidCompilationLevel() {
    assertCompileBadRequest(
            """
            {
              "payload": "function hello() {}",
              "compilationLevel": "MINIFY",
              "warningLevel": "DEFAULT",
              "outputFileName": "default.js",
              "externalScripts": {"urls": []}
            }
            """);
  }

  @Test
  public void testCompileRejectsInvalidWarningLevel() {
    assertCompileBadRequest(
            """
            {
              "payload": "function hello() {}",
              "compilationLevel": "Simple",
              "warningLevel": "LOUD",
              "outputFileName": "default.js",
              "externalScripts": {"urls": []}
            }
            """);
  }

  @Test
  public void testCompileRejectsInvalidLanguageLevel() {
    assertCompileBadRequest(
            """
            {
              "payload": "function hello() {}",
              "compilationLevel": "Simple",
              "warningLevel": "DEFAULT",
              "outputFileName": "default.js",
              "language": {
                "languageIn": "ECMASCRIPT_2021",
                "languageOut": "INVALID"
              },
              "externalScripts": {"urls": []}
            }
            """);
  }

  @Test
  public void testReadMissingCacheEntryReturnsJsonNotFound() {
    given()
            .when()
            .get("/compile/missing")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("status", is(404))
            .body("error", containsString("No compiled code found"))
            .body("stack", nullValue());
  }

  @Test
  public void testDownloadUsesDefaultFileNameWhenQueryIsMissing() {
    String downloadId = compileCollisionPayload("default-name").path("downloadId");

    given()
        .when().get("/compile/{downloadId}", downloadId)
        .then()
        .statusCode(200)
        .header("Content-Disposition", is("attachment; filename=\"default.js\""));
  }

  @Test
  public void testDownloadUsesRequestedFileName() {
    String downloadId = compileCollisionPayload("custom-name").path("downloadId");

    given()
        .queryParam("filename", "compiled-output.js")
        .when().get("/compile/{downloadId}", downloadId)
        .then()
        .statusCode(200)
        .header("Content-Disposition", is("attachment; filename=\"compiled-output.js\""));
  }

  @Test
  public void testDownloadRejectsInvalidFileName() {
    String downloadId = compileCollisionPayload("invalid-name").path("downloadId");

    given()
        .queryParam("filename", "../compiled.js")
        .when().get("/compile/{downloadId}", downloadId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("status", is(400))
        .body("error", notNullValue())
        .body("stack", nullValue());
  }

  @Test
  public void testHashCollisionDownloadsMatchingCompiledCode() {
    io.restassured.response.Response first = compileCollisionPayload("Aa");
    io.restassured.response.Response second = compileCollisionPayload("BB");

    String firstId = first.path("downloadId");
    String secondId = second.path("downloadId");
    assertNotEquals(firstId, secondId);
    assertTrue(secondId.startsWith(firstId + "-"));

    assertEquals(first.path("compiledCode"), download(firstId));
    assertEquals(second.path("compiledCode"), download(secondId));
  }

  @Test
  public void testUnknownRouteReturnsJsonNotFound() {
    given()
            .when()
            .get("/missing-route")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("status", is(404))
            .body("error", is("Resource not found"))
            .body("stack", nullValue());
  }

  @Test
  public void testOpenApiYamlIsGeneratedFromAnnotations() {
    String openApiYaml = given()
            .when()
            .get("/openapi.yaml")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    assertTrue(openApiYaml.contains("openapi: 3.0.3"));
    assertTrue(openApiYaml.contains("title: JSCompressor API"));
    assertTrue(openApiYaml.contains("url: https://jscompressor.treblereel.dev/"));
    assertTrue(openApiYaml.contains("description: Production server"));
    assertTrue(openApiYaml.contains("/compile:"));
    assertTrue(openApiYaml.contains("operationId: compileJavaScript"));
    assertTrue(openApiYaml.contains("operationId: readCompiledCode"));
    assertTrue(openApiYaml.contains("name: filename"));
    assertTrue(openApiYaml.contains("ErrorResponse"));
    assertTrue(openApiYaml.contains("\"400\""));
    assertTrue(openApiYaml.contains("\"404\""));
    assertTrue(openApiYaml.contains("\"413\""));
    assertTrue(openApiYaml.contains("\"429\""));
    assertTrue(openApiYaml.contains("\"500\""));
  }

  @Test
  public void testLegacyOpenApiPathRemainsAvailable() {
    String openApi = given()
        .when()
        .get("/openapi")
        .then()
        .statusCode(200)
        .extract()
        .asString();

    assertTrue(openApi.contains("operationId: compileJavaScript"));
    assertTrue(openApi.contains("operationId: readCompiledCode"));
  }

  @Test
  public void testIndexIncludesKeyboardAndScreenReaderSemantics() {
    String index = given()
        .when().get("/")
        .then()
        .statusCode(200)
        .extract().asString();

    assertTrue(index.contains("href=\"#main-content\""));
    assertTrue(index.contains("role=\"separator\""));
    assertTrue(index.contains("role=\"tablist\""));
    assertTrue(index.contains("role=\"tabpanel\""));
    assertTrue(index.contains("role=\"status\""));
    assertTrue(index.contains("role=\"dialog\""));
    assertTrue(index.contains("aria-live=\"polite\""));
    assertTrue(index.contains("aria-label=\"Close terms of service\""));
    assertTrue(index.contains("href=\"https://github.com/sponsors/treblereel\""));
    assertTrue(index.contains("aria-label=\"Support me on GitHub Sponsors\""));
  }

  @Test
  public void testIndexUsesSelfHostedFrontendAssets() {
    String index = given()
        .when().get("/")
        .then()
        .statusCode(200)
        .extract().asString();

    assertTrue(index.contains("href=\"assets/app.css\""));
    assertTrue(index.contains("src=\"assets/app.js\""));
    assertTrue(index.contains("src=\"assets/alpine.min.js\""));
    assertFalse(index.contains("cdn.tailwindcss.com"));
    assertFalse(index.contains("cdn.jsdelivr.net"));

    given().when().get("/assets/app.css").then().statusCode(200);
    given().when().get("/assets/app.js").then().statusCode(200);
    given().when().get("/assets/alpine.min.js").then().statusCode(200);
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
    AtomicInteger unexpectedCount = new AtomicInteger();
    AtomicInteger exceptionCount = new AtomicInteger();

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
            unexpectedCount.incrementAndGet();
            System.out.println("Unexpected status code: " + statusCode);
          }
        } catch (Exception e) {
          exceptionCount.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    assertTrue(successCount.get() <= 10, "No more than 10 requests should be accepted");
    assertTrue(bulkheadRejectedCount.get() >= 5, "No less than 5 requests should be rejected");
    assertEquals(0, unexpectedCount.get(), "No unexpected status codes should be returned");
    assertEquals(0, exceptionCount.get(), "No requests should fail with exceptions");
  }

  // export MAX_DOWNLOAD_FILE_SIZE=20971520 before test
  @Test
  public void testCompileLargerFile() {
    CompileRequest compileRequest = new CompileRequest();

    StringBuilder sb = new StringBuilder();
    int chuckSize = 100;
    int chuckCount = 20000;

    for (int i = 0; i < chuckCount; i++) {
      sb.append("var t" + i + " = \"");
      sb.append("a".repeat(chuckSize));
      sb.append("\"\n");
    }

    String largeString = sb.toString();
    compileRequest.setPayload(largeString);
    compileRequest.setCompilationLevel("Advanced");
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());

    String json = jsonb.toJson(compileRequest);

    //write json to file
    //try (FileWriter file = new FileWriter("compileRequest.json")) {
    //  file.write(json);
    //} catch (IOException e) {
    //  throw new RuntimeException(e);
    //}

    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(200)
            .body("compiledCode", is("'use strict';"))
            .body("downloadId", notNullValue())
            .body("errors", hasSize(0))
            .body("statistics.compiledSize", is(13))
            .body("statistics.originalSize", is(2308890))
            .body("warnings", hasSize(0));
  }

  private io.restassured.response.Response compileCollisionPayload(String value) {
    return given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "payload": "alert(\\"%s\\");",
              "outputFileName": "collision-test.js",
              "compilationLevel": "Whitespace only"
            }
            """.formatted(value))
        .when().post("/compile")
        .then().statusCode(200)
        .extract().response();
  }

  private String download(String downloadId) {
    return given()
        .when().get("/compile/{downloadId}", downloadId)
        .then().statusCode(200)
        .extract().asString();
  }

  private void assertCompileBadRequest(String json) {
    given()
            .contentType(ContentType.JSON)
            .body(json)
            .when()
            .post("/compile")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("status", is(400))
            .body("error", notNullValue())
            .body("stack", nullValue());
  }

  private CompileRequest createCompileRequest(String compilationLevel) {
    CompileRequest compileRequest = new CompileRequest();
    compileRequest.setPayload("function hello() {}");
    compileRequest.setCompilationLevel(compilationLevel);
    compileRequest.setWarningLevel("DEFAULT");
    compileRequest.setOutputFileName("default.js");
    compileRequest.setFormatting(new Formatting(false, false));
    compileRequest.setExternalScripts(new ExternalScripts());
    return compileRequest;
  }

}
