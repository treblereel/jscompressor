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

import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.config.ServerConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RateLimiterTest {

  @Test
  public void testAllowsRequestsUnderLimit() {
    RateLimiter rateLimiter = createRateLimiter(true, 2);

    assertTrue(rateLimiter.isAllowed("client"));
    assertTrue(rateLimiter.isAllowed("client"));
  }

  @Test
  public void testRejectsRequestsOverLimitWithinWindow() {
    RateLimiter rateLimiter = createRateLimiter(true, 2);

    assertTrue(rateLimiter.isAllowed("client", 2, 1_000));
    assertTrue(rateLimiter.isAllowed("client", 2, 2_000));
    assertFalse(rateLimiter.isAllowed("client", 2, 3_000));
  }

  @Test
  public void testAllowsAgainAfterWindowResets() {
    RateLimiter rateLimiter = createRateLimiter(true, 1);

    assertTrue(rateLimiter.isAllowed("client", 1, 1_000));
    assertFalse(rateLimiter.isAllowed("client", 1, 2_000));
    assertTrue(rateLimiter.isAllowed("client", 1, 61_000));
  }

  @Test
  public void testLimitsClientsIndependently() {
    RateLimiter rateLimiter = createRateLimiter(true, 1);

    assertTrue(rateLimiter.isAllowed("client-a", 1, 1_000));
    assertFalse(rateLimiter.isAllowed("client-a", 1, 2_000));
    assertTrue(rateLimiter.isAllowed("client-b", 1, 2_000));
  }

  @Test
  public void testDisabledRateLimitAllowsRequests() {
    RateLimiter rateLimiter = createRateLimiter(false, 1);

    assertTrue(rateLimiter.isAllowed("client"));
    assertTrue(rateLimiter.isAllowed("client"));
    assertTrue(rateLimiter.isAllowed("client"));
  }

  private static RateLimiter createRateLimiter(boolean enabled, long requestsPerMinute) {
    RateLimiter rateLimiter = new RateLimiter();
    rateLimiter.serverConfig = () -> new TestServerConfig(enabled, requestsPerMinute);
    return rateLimiter;
  }

  private record TestServerConfig(boolean rateLimitEnabled, long rateLimitRequestsPerMinute)
      implements ServerConfig {

    @Override
    public long downloadFileMaxSize() {
      return 1024;
    }

    @Override
    public long requestBodyMaxSize() {
      return 0;
    }

    @Override
    public long cacheMaxSize() {
      return 1024;
    }

    @Override
    public long downloadUrlsPreRequest() {
      return 10;
    }

    @Override
    public int downloadConnectTimeoutMs() {
      return 5000;
    }

    @Override
    public int downloadTimeoutMs() {
      return 30000;
    }

    @Override
    public int downloadReadTimeoutMs() {
      return 10000;
    }

    @Override
    public int downloadMaxRedirects() {
      return 5;
    }

    @Override
    public String cacheLocation() {
      return "cache";
    }
  }
}
