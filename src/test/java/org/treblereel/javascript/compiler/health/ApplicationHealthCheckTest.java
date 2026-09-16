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

package org.treblereel.javascript.compiler.health;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ApplicationHealthCheckTest {

  @Test
  public void exposesRegisteredHealthChecks() {
    assertCheck("/health/started", "Application startup check");
    assertCheck("/health/live", "Application liveness check");
    assertCheck("/health/ready", "Application readiness check");
  }

  private void assertCheck(String path, String name) {
    given()
        .when().get(path)
        .then()
        .statusCode(200)
        .body("status", is("UP"))
        .body("checks.name", hasItem(name))
        .body("checks.find { it.name == '" + name + "' }.status", is("UP"));
  }
}
