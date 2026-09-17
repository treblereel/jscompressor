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

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.treblereel.javascript.compiler.config.ServerConfig;

@ApplicationScoped
public class FileCache {

  private final AtomicLong currentSize = new AtomicLong();
  private final AtomicLong currentFiles = new AtomicLong();
  private final AtomicLong evictions = new AtomicLong();

  @Inject
  ServerConfig serverConfig;

  @PostConstruct
  public void init() {
    try {
      Files.createDirectories(cacheDirectory());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create cache location: " + serverConfig.cacheLocation(), e);
    }
    if (!Files.isDirectory(cacheDirectory())) {
      throw new RuntimeException("Cache location is not a directory: " + serverConfig.cacheLocation());
    }
    refreshUsage();
  }

  public synchronized String put(String filename, byte[] content) throws Exception {
    if (content.length > serverConfig.cacheMaxSize()) {
      throw new Exception("File is larger than cache max size");
    }

    String candidate = filename;
    int collision = 0;
    while (true) {
      Path path = resolveCachePath(candidate);
      if (Files.exists(path)) {
        try {
          if (Arrays.equals(Files.readAllBytes(path), content)) {
            return candidate;
          }
        } catch (NoSuchFileException ignored) {
          continue;
        }
        candidate = filename + "-" + ++collision;
        continue;
      }

      evict(content);
      writeFileToCache(path, content);
      currentSize.addAndGet(content.length);
      currentFiles.incrementAndGet();
      return candidate;
    }
  }

  public synchronized byte[] get(String filename) throws Exception {
    Path path = resolveCachePath(filename);
    if (!Files.exists(path)) {
      return null;
    }
    try {
      return Files.readAllBytes(path);
    } catch (NoSuchFileException e) {
      return null;
    }
  }

  private void writeFileToCache(Path path, byte[] content) throws Exception {
    Path tempFile = Files.createTempFile(cacheDirectory(), ".cache-", ".tmp");
    try {
      Files.write(tempFile, content);
      try {
        Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tempFile, path);
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private void evict(byte[] content) throws Exception {
    Path path = cacheDirectory();
    if (!Files.isDirectory(path)) {
      throw new Exception("Cache location is not a directory: " + serverConfig.cacheLocation());
    }

    File[] files = path.toFile().listFiles();
    if (files == null) {
      throw new Exception("Cache location is not readable: " + serverConfig.cacheLocation());
    }

    List<File> cachedFiles = Arrays.stream(files)
        .filter(File::isFile)
        .sorted(Comparator.comparingLong(File::lastModified))
        .toList();
    long totalSize = cachedFiles.stream().mapToLong(File::length).sum();

    for (File file : cachedFiles) {
      if (totalSize + content.length <= serverConfig.cacheMaxSize()) {
        return;
      }
      long fileSize = file.length();
      if (Files.deleteIfExists(file.toPath())) {
        totalSize -= fileSize;
        currentSize.addAndGet(-fileSize);
        currentFiles.decrementAndGet();
        evictions.incrementAndGet();
      }
    }

    if (totalSize + content.length > serverConfig.cacheMaxSize()) {
      throw new Exception("Unable to free enough cache space");
    }
  }

  private Path resolveCachePath(String filename) throws Exception {
    if (filename == null || filename.isBlank()) {
      throw new Exception("Invalid cache filename");
    }

    Path filenamePath = Paths.get(filename);
    if (filenamePath.isAbsolute()) {
      throw new Exception("Invalid cache filename");
    }
    if (filenamePath.getNameCount() != 1) {
      throw new Exception("Invalid cache filename");
    }

    Path path = cacheDirectory().resolve(filenamePath).normalize();
    if (!path.startsWith(cacheDirectory())) {
      throw new Exception("Invalid cache filename");
    }
    return path;
  }

  private Path cacheDirectory() {
    return Paths.get(serverConfig.cacheLocation()).toAbsolutePath().normalize();
  }

  private void refreshUsage() {
    File[] files = cacheDirectory().toFile().listFiles(File::isFile);
    if (files == null) {
      throw new RuntimeException("Cache location is not readable: " + serverConfig.cacheLocation());
    }
    currentSize.set(Arrays.stream(files).mapToLong(File::length).sum());
    currentFiles.set(files.length);
  }

  public long currentSize() {
    return currentSize.get();
  }

  public long currentFiles() {
    return currentFiles.get();
  }

  public long evictions() {
    return evictions.get();
  }
}
