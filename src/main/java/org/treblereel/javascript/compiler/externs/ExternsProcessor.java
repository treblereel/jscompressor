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

package org.treblereel.javascript.compiler.externs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DefaultExterns;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.jarjar.com.google.common.base.Ascii;

@ApplicationScoped
public class ExternsProcessor {

  private Map<String, String> browserExterns = Map.of();

  public List<SourceFile> getExterns() {
    return createExterns(CompilerOptions.Environment.BROWSER, browserExterns);
  }

  @PostConstruct
  public void init() {
    try {
      browserExterns = readBuiltinExterns(CompilerOptions.Environment.BROWSER);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<SourceFile> getBuiltinExterns(CompilerOptions.Environment env) throws IOException {
    return createExterns(env, readBuiltinExterns(env));
  }

  private Map<String, String> readBuiltinExterns(CompilerOptions.Environment env) throws IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream input =
        classLoader.getResourceAsStream("META-INF/resources/buildin/externs.zip")) {
      if (input == null) {
        throw new IOException("Resource not found: META-INF/resources/buildin/externs.zip");
      }
      ZipInputStream zip = new ZipInputStream(input);
      String envPrefix = Ascii.toLowerCase(env.toString()) + "/";
      Map<String, String> externs = new HashMap<>();
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        if (entry.isDirectory()) {
          continue;
        }
        String filename = entry.getName();

        if (filename.contains("/")) {
          if (!filename.startsWith(envPrefix)) {
            continue;
          }
          filename = filename.substring(envPrefix.length());
        }

        externs.put(filename, new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
      return Map.copyOf(externs);
    }
  }

  private List<SourceFile> createExterns(CompilerOptions.Environment env, Map<String, String> externs) {
    Map<String, SourceFile> mapFromExternsZip = new HashMap<>();
    externs.forEach(
        (filename, content) ->
            mapFromExternsZip.put(
                filename,
                SourceFile.builder()
                    .withPath("externs.zip//" + filename)
                    .withContent(content)
                    .build()));
    return DefaultExterns.prepareExterns(env, mapFromExternsZip);
  }
}
