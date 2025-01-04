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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

@ApplicationScoped
public class ApplicationHealthCheck {

  @Startup
  @Produces
  public HealthCheckResponse onStart() {
    return HealthCheckResponse.named("Application startup check").up().build();
  }

  @Liveness
  @Produces
  public HealthCheckResponse check() {
    return HealthCheckResponse.named("Application liveness check").up().build();
  }

  @Readiness
  @Produces
  public HealthCheckResponse readiness() {
    return HealthCheckResponse.named("Application readiness check").up().build();
  }
}
