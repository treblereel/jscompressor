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

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import org.treblereel.javascript.compiler.cache.FileCache;
import org.treblereel.javascript.compiler.config.ServerConfig;

@ApplicationScoped
@Startup
public class CacheMetrics {

  @Inject
  FileCache cache;

  @Inject
  ServerConfig serverConfig;

  @Inject
  MeterRegistry registry;

  @PostConstruct
  void register() {
    Gauge.builder("jscompressor.cache.usage", cache, FileCache::currentSize)
        .description("Current cache usage")
        .baseUnit("bytes")
        .strongReference(true)
        .register(registry);
    Gauge.builder("jscompressor.cache.files", cache, FileCache::currentFiles)
        .description("Current number of cached files")
        .strongReference(true)
        .register(registry);
    Gauge.builder("jscompressor.cache.limit", serverConfig, ServerConfig::cacheMaxSize)
        .description("Configured cache size limit")
        .baseUnit("bytes")
        .strongReference(true)
        .register(registry);
    FunctionCounter.builder("jscompressor.cache.evictions", cache, FileCache::evictions)
        .description("Files evicted from the cache")
        .register(registry);
  }
}
