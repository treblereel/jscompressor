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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.treblereel.javascript.compiler.config.ServerConfig;

@ApplicationScoped
public class RateLimiter {

  static final long WINDOW_MILLIS = 60_000;

  @Inject
  jakarta.inject.Provider<ServerConfig> serverConfig;

  private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong lastCleanupMillis = new AtomicLong();

  public boolean isAllowed(String clientId) {
    ServerConfig config = serverConfig.get();
    if (!config.rateLimitEnabled() || config.rateLimitRequestsPerMinute() <= 0) {
      return true;
    }
    return isAllowed(clientId, config.rateLimitRequestsPerMinute(), System.currentTimeMillis());
  }

  boolean isAllowed(String clientId, long limit, long nowMillis) {
    cleanupExpiredWindows(nowMillis);
    String key = clientId == null || clientId.isBlank() ? "unknown" : clientId;
    boolean[] allowed = {true};
    windows.compute(key, (ignored, window) -> {
      if (window == null || nowMillis - window.startedAtMillis >= WINDOW_MILLIS) {
        return new Window(nowMillis, 1);
      }
      if (window.count >= limit) {
        allowed[0] = false;
        return window;
      }
      return new Window(window.startedAtMillis, window.count + 1);
    });
    return allowed[0];
  }

  private void cleanupExpiredWindows(long nowMillis) {
    long lastCleanup = lastCleanupMillis.get();
    if (nowMillis - lastCleanup < WINDOW_MILLIS) {
      return;
    }
    if (!lastCleanupMillis.compareAndSet(lastCleanup, nowMillis)) {
      return;
    }
    windows.entrySet().removeIf(entry -> nowMillis - entry.getValue().startedAtMillis >= WINDOW_MILLIS);
  }

  private record Window(long startedAtMillis, long count) {
  }
}
