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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class CompileEndpointIT {

    @TestHTTPResource("/compile")
    URI compileUri;

    @TestHTTPResource("js/test1.js")
    URI testJs1;

    @TestHTTPResource("js/test2.js")
    URI testJs2;

    @TestHTTPResource("js/test3.js")
    URI testJs3;

    @TestHTTPResource("js/test4.js")
    URI testJs4;

    @TestHTTPResource("js/test5.js")
    URI testJs5;

    @TestHTTPResource("js/test6.js")
    URI testJs6;

    @TestHTTPResource("js/test7.js")
    URI testJs7;

    @TestHTTPResource("js/test8.js")
    URI testJs8;

    @TestHTTPResource("js/test9.js")
    URI testJs9;

    @TestHTTPResource("js/test10.js")
    URI testJs10;

    @TestHTTPResource("js/test11.js")
    URI testJs11;

    @TestHTTPResource("js/test12.js")
    URI testJs12;

    @TestHTTPResource("js/test13.js")
    URI testJs13;

    @TestHTTPResource("js/test14.js")
    URI testJs14;

    @TestHTTPResource("js/test15.js")
    URI testJs15;

    @TestHTTPResource("js/test16.js")
    URI testJs16;

    @TestHTTPResource("js/test17.js")
    URI testJs17;

    @TestHTTPResource("js/test18.js")
    URI testJs18;

    @TestHTTPResource("js/test19.js")
    URI testJs19;

    @TestHTTPResource("js/test20.js")
    URI testJs20;


    private static Duration timeout() {
        return Duration.ofSeconds(Long.getLong("http.timeout.seconds", 60));
    }

    @Test
    void compileShouldSucceed() throws Exception {

        String json =
                """
                        {
                          "payload": "",
                          "warningLevel": "DEFAULT",
                          "compilationLevel": "Simple",
                          "formatting": {
                            "prettyPrint": false,
                            "printInputDelimiter": false
                          },
                          "outputFileName": "default.js",
                          "externalScripts": {
                            "urls": [
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s",
                              "%s"
                            ]
                          }
                        }
                        """.formatted(testJs1,
                        testJs2,
                        testJs3,
                        testJs4,
                        testJs5,
                        testJs6,
                        testJs7,
                        testJs8,
                        testJs9,
                        testJs10,
                        testJs11,
                        testJs12,
                        testJs13,
                        testJs14,
                        testJs15,
                        testJs16,
                        testJs17,
                        testJs18,
                        testJs19,
                        testJs20
                );

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout())
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(compileUri)
                .timeout(timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(
                200,
                response.statusCode(),
                () -> "Unexpected status: " + response.statusCode() + "\nBody:\n" + response.body());

        String contentType = response.headers().firstValue("content-type").orElse("");
        assertFalse(contentType.isBlank(), "Content-Type header must be present");

        String body = response.body();
        assertNotNull(body, "Body must not be null");
        assertFalse(body.isEmpty(), "Body must not be empty");
    }
}
