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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;
import org.treblereel.javascript.compiler.cache.FileCache;
import org.treblereel.javascript.compiler.config.ServerConfig;
import org.treblereel.javascript.compiler.domain.CompileRequest;
import org.treblereel.javascript.compiler.domain.CompileResponse;
import org.treblereel.javascript.compiler.domain.Statistics;
import org.treblereel.javascript.compiler.downloader.FileDownloader;
import org.treblereel.javascript.compiler.externs.ExternsProcessor;

@Path("/compile")
public class CompilerResource {

  @Inject ExternsProcessor externsProcessor;

  @Inject RoutingContext context;

  @Inject Logger logger;

  @Inject FileDownloader fileDownloader;

  @Inject FileCache cache;

  @Inject ServerConfig serverConfig;

  @PostConstruct
  public void init() {
    System.out.println("Cache max size: " + serverConfig.cacheMaxSize());
    System.out.println("Download file max size: " + serverConfig.downloadFileMaxSize());
    System.out.println("Download urls per request: " + serverConfig.downloadUrlsPreRequest());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Bulkhead(value = 5, waitingTaskQueue = 5)
  @Timeout(value = 120, unit = ChronoUnit.SECONDS)
  public Response compile(@Valid CompileRequest request) {
    logger.info("received request from " + context.request().remoteAddress().host());

    long start = System.currentTimeMillis();

    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();

    switch (request.getCompilationLevel()) {
      case "Simple":
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        break;
      case "Advanced":
        CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        break;
      case "Whitespace only":
        CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
        break;
      default:
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    }

    switch (request.getWarningLevel()) {
      case "QUIET":
        WarningLevel.QUIET.setOptionsForWarningLevel(options);
        break;
      case "VERBOSE":
        WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
        break;
      case "DEFAULT":
      default:
        WarningLevel.DEFAULT.setOptionsForWarningLevel(options);
    }

    List<SourceFile> sources = new ArrayList<>();
    long externalSourceSize = 0;

    for (String url : request.getExternalScripts().getUrls()) {
      try {
        SourceFile file = fileDownloader.downloadFile(url);
        sources.add(file);
        externalSourceSize += file.getCode().length();
      } catch (IOException e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "Failed to download file: " + e.getMessage()))
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
    }

    options.setEnvironment(CompilerOptions.Environment.BROWSER);
    options.setDependencyOptions(DependencyOptions.sortOnly());
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2021);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2021);

    if (request.getFormatting() != null) {
      options.setPrettyPrint(request.getFormatting().prettyPrint);
      options.setPrintInputDelimiter(request.getFormatting().printInputDelimiter);
    }

    options.setCheckTypes(true);

    compiler.initOptions(options);
    // compiler.disableThreads();

    sources.add(SourceFile.fromCode("input.js", request.getPayload()));

    compiler.compile(externsProcessor.getExterns(), sources, options);

    Result result = compiler.getResult();
    CompileResponse response = new CompileResponse();

    if (result.success) {
      response.setCompiledCode(compiler.toSource());
      String hash = Long.toHexString(request.hashCode());
      try {
        cache.put(hash, compiler.toSource().getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", "Failed to cache compiled code: " + e.getMessage()))
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
      response.setDownloadId(hash);
    } else {
      response.setCompiledCode(null);
    }

    List<String> warnings =
        result.warnings.stream().map(JSError::toString).collect(Collectors.toList());
    List<String> errors =
        result.errors.stream().map(JSError::toString).collect(Collectors.toList());

    response.setWarnings(warnings);
    response.setErrors(errors);

    Statistics stats = new Statistics();
    stats.setOriginalSize(request.getPayload().length() + externalSourceSize);
    stats.setCompiledSize(
        response.getCompiledCode() != null ? response.getCompiledCode().length() : 0);
    response.setStatistics(stats);

    logger.info(
        "compilation took "
            + (System.currentTimeMillis() - start)
            + "ms, payload size: "
            + stats.getOriginalSize()
            + " bytes, compiled size: "
            + stats.getCompiledSize()
            + " bytes");
    return Response.ok(response).build();
  }

  @GET
  @Path("/{hash}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Bulkhead(value = 10, waitingTaskQueue = 5)
  @Timeout(value = 20, unit = ChronoUnit.SECONDS)
  public Response read(@PathParam("hash") String hash) {
    byte[] bytes = new byte[0];
    try {
      bytes = cache.get(hash);
    } catch (Exception e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("No compiled code found for hash " + hash)
          .build();
    }
    if (bytes == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity("No compiled code found for hash " + hash)
          .build();
    }
    return Response.ok(bytes)
        .header("Content-Disposition", "attachment; filename=\"default.js\"")
        .build();
  }
}
