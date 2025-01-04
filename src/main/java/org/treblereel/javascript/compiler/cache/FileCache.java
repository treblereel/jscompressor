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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.runtime.annotations.StaticInitSafe;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FileCache {

  @StaticInitSafe
  @ConfigProperty(name = "cache.location", defaultValue = "cache")
  String location;


  @StaticInitSafe
  @ConfigProperty(name = "cache.max-size", defaultValue = "102400")
  long maxCacheSize;

  @PostConstruct
  public void init() {
    Path path = Paths.get(location);
    if (!path.toFile().exists()) {
      throw new RuntimeException("Cache location does not exist: " + location);
    }
    if (path.resolve("cache").toFile().exists()) {
      path.resolve("cache").toFile().mkdirs();
    }
  }

  public void put(String filename, byte[] content) throws Exception {
    if (checkFileExists(filename)) {
      return;
    }

    evict(content);
    writeFileToCache(filename, content);
  }

  public byte[] get(String filename) throws Exception {
    Path path = Paths.get(location, filename);
    if (!path.toFile().exists()) {
      return null;
    }
    return Files.readAllBytes(path);
  }

  private boolean checkFileExists(String filename) {
    Path path = Paths.get(location, filename);
    return path.toFile().exists();
  }

  private void writeFileToCache(String filename, byte[] content) throws Exception {
    Path path = Paths.get(location, filename);
    path.toFile().createNewFile();
    Files.write(path, content);
  }

  private void evict(byte[] content) throws Exception {
    while (!checkSpace(content)) {
      try {
        File file = findOldestFile();
        if (file != null) {
          file.delete();
        } else {
          break;
        }
      } catch (Exception e) {
        throw new Exception(e);
      }
    }
  }

  private boolean checkSpace(byte[] content) throws Exception {
    Path path = Paths.get(location);

    if (!path.toFile().exists()) {
      throw new Exception("Cache location does not exist: " + location);
    }

    File directory = Paths.get(location).toFile();
    long totalSize =
        Arrays.stream(directory.listFiles()).filter(File::isFile).mapToLong(File::length).sum();
    return totalSize + content.length <= maxCacheSize;
  }

  private File findOldestFile() throws Exception {
    File directory = Paths.get(location).toFile();

    if (!directory.isDirectory()) {
      throw new Exception("Cache location is not a directory: " + location);
    }

    File[] files = directory.listFiles();

    if (files == null || files.length == 0) {
      return null;
    }

    return Arrays.stream(files)
        .filter(File::isFile)
        .min(Comparator.comparingLong(File::lastModified))
        .orElse(null);
  }
}
