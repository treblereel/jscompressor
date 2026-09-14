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

package org.treblereel.javascript.compiler.cache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.treblereel.javascript.compiler.config.ServerConfig;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileCacheTest {

  @TempDir
  Path tempDir;

  @Test
  public void initCreatesCacheDirectory() {
    Path cacheDir = tempDir.resolve("missing-cache");

    FileCache cache = createCache(cacheDir, 100);
    cache.init();

    assertTrue(Files.isDirectory(cacheDir));
  }

  @Test
  public void putEvictsOldestFilesUntilThereIsEnoughSpace() throws Exception {
    Path cacheDir = tempDir.resolve("cache");
    Files.createDirectories(cacheDir);
    FileCache cache = createCache(cacheDir, 10);

    cache.put("old", bytes("1234"));
    cache.put("newer", bytes("5678"));
    Files.setLastModifiedTime(cacheDir.resolve("old"), FileTime.fromMillis(1000));
    Files.setLastModifiedTime(cacheDir.resolve("newer"), FileTime.fromMillis(2000));

    cache.put("fresh", bytes("abcd"));

    assertFalse(Files.exists(cacheDir.resolve("old")));
    assertArrayEquals(bytes("5678"), cache.get("newer"));
    assertArrayEquals(bytes("abcd"), cache.get("fresh"));
  }

  @Test
  public void putRejectsFilesLargerThanCacheMaxSizeWithoutEvicting() throws Exception {
    Path cacheDir = tempDir.resolve("cache");
    Files.createDirectories(cacheDir);
    FileCache cache = createCache(cacheDir, 5);

    cache.put("existing", bytes("1234"));

    assertThrows(Exception.class, () -> cache.put("huge", bytes("123456")));
    assertArrayEquals(bytes("1234"), cache.get("existing"));
    assertNull(cache.get("huge"));
  }

  @Test
  public void rejectsUnsafeCacheFilenames() {
    Path cacheDir = tempDir.resolve("cache");
    FileCache cache = createCache(cacheDir, 100);

    assertThrows(Exception.class, () -> cache.put("../outside", bytes("x")));
    assertThrows(Exception.class, () -> cache.put("nested/file", bytes("x")));
    assertThrows(Exception.class, () -> cache.get("../outside"));
    assertThrows(Exception.class, () -> cache.get(""));
  }

  @Test
  public void putDoesNotLeaveTemporaryFiles() throws Exception {
    Path cacheDir = tempDir.resolve("cache");
    Files.createDirectories(cacheDir);
    FileCache cache = createCache(cacheDir, 100);

    cache.put("entry", bytes("content"));

    assertArrayEquals(bytes("content"), cache.get("entry"));
    try (var files = Files.list(cacheDir)) {
      assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".cache-")));
    }
  }

  @Test
  public void concurrentPutsKeepCacheWithinLimit() throws Exception {
    Path cacheDir = tempDir.resolve("cache");
    Files.createDirectories(cacheDir);
    FileCache cache = createCache(cacheDir, 100);

    int totalTasks = 10;
    ExecutorService executor = Executors.newFixedThreadPool(totalTasks);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < totalTasks; i++) {
      String filename = "entry-" + i;
      futures.add(executor.submit(() -> {
        start.await();
        cache.put(filename, bytes("12345678901234567890"));
        return null;
      }));
    }

    start.countDown();
    for (Future<?> future : futures) {
      future.get();
    }
    executor.shutdown();

    try (var files = Files.list(cacheDir)) {
      long totalSize = files.filter(Files::isRegularFile).mapToLong(path -> path.toFile().length()).sum();
      assertTrue(totalSize <= 100);
    }
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static FileCache createCache(Path cacheDir, long cacheMaxSize) {
    FileCache cache = new FileCache();
    cache.serverConfig = new TestServerConfig(cacheDir, cacheMaxSize);
    return cache;
  }

  private record TestServerConfig(Path cacheDir, long cacheMaxSize) implements ServerConfig {

    @Override
    public long downloadFileMaxSize() {
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
    public int downloadReadTimeoutMs() {
      return 10000;
    }

    @Override
    public int downloadMaxRedirects() {
      return 5;
    }

    @Override
    public String cacheLocation() {
      return cacheDir.toString();
    }

    @Override
    public boolean rateLimitEnabled() {
      return true;
    }

    @Override
    public long rateLimitRequestsPerMinute() {
      return 120;
    }
  }
}
