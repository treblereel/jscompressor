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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

  private static final PostgreSQLContainer<?> PG =
          new PostgreSQLContainer<>("postgres:15")
                  .withDatabaseName("jscompressor")
                  .withUsername("postgres")
                  .withPassword("postgres");

  @Override
  public Map<String, String> start() {
    PG.start();
    return Map.of(
            "quarkus.datasource.jdbc.url", PG.getJdbcUrl(),
            "quarkus.datasource.username",    PG.getUsername(),
            "quarkus.datasource.password",    PG.getPassword(),
            "quarkus.datasource.reactive.url",
            String.format("postgresql://%s:%d/%s",
                    PG.getHost(),
                    PG.getFirstMappedPort(),
                    PG.getDatabaseName())
    );
  }

  @Override
  public void stop() {
    PG.stop();
  }
}
