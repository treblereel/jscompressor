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
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.logging.Logger;
import org.treblereel.javascript.compiler.cache.FileCache;
import org.treblereel.javascript.compiler.config.ServerConfig;
import org.treblereel.javascript.compiler.domain.CompileRequest;
import org.treblereel.javascript.compiler.domain.CompileResponse;
import org.treblereel.javascript.compiler.domain.ErrorResponse;
import org.treblereel.javascript.compiler.domain.Statistics;
import org.treblereel.javascript.compiler.downloader.FileDownloader;
import org.treblereel.javascript.compiler.externs.ExternsProcessor;
import org.treblereel.javascript.compiler.metrics.CompilationMetrics;
import org.treblereel.javascript.compiler.validation.ValidFileName;

@Path("/compile")
public class CompilerResource {

  @Inject ExternsProcessor externsProcessor;

  @Inject RoutingContext context;

  @Inject Logger logger;

  @Inject FileDownloader fileDownloader;

  @Inject FileCache cache;

  @Inject ServerConfig serverConfig;

  @Inject CompilationMetrics compilationMetrics;

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
  @Operation(
          summary = "Compile JavaScript code",
          description = "Compiles JavaScript code using Google Closure Compiler",
          operationId = "compile"
  )
  @RequestBody(
          content  = @Content(
                  mediaType = "application/json",
                  schema    = @Schema(implementation = CompileRequest.class)
          )
  )
  @APIResponses(
          value = {
              @APIResponse(
                      responseCode = "200",
                      description  = "OK",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = CompileResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "400",
                      description  = "Invalid compile request",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "413",
                      description  = "Request body exceeds the configured size limit",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "429",
                      description  = "Too many requests",
                      headers      = @Header(
                              name        = "Retry-After",
                              description = "Seconds before retrying the request",
                              schema      = @Schema(type = SchemaType.INTEGER)
                      ),
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "500",
                      description  = "Internal server error",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              )
          }
  )
  public Response compile(@NotNull @Valid CompileRequest request) {
    try (CompilationMetrics.Observation observation =
             compilationMetrics.start(request.getPayload().length())) {
      return compile(request, observation);
    }
  }

  private Response compile(CompileRequest request, CompilationMetrics.Observation observation) {
    logger.info("received request from " + context.request().remoteAddress().host());

    long start = System.currentTimeMillis();

    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();

    switch (request.getCompilationLevel() == null ? "Simple" : request.getCompilationLevel()) {
      case "SIMPLE":
      case "Simple":
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        break;
      case "ADVANCED":
      case "Advanced":
        CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        break;
      case "WHITESPACE":
      case "Whitespace only":
        CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
        break;
      default:
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    }

    switch (request.getWarningLevel() == null ? "DEFAULT" : request.getWarningLevel()) {
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
    List<String> externalScriptUrls = List.of();
    if (request.getExternalScripts() != null && request.getExternalScripts().getUrls() != null) {
      externalScriptUrls = request.getExternalScripts().getUrls();
    }

    for (String url : externalScriptUrls) {
      try {
        SourceFile file = fileDownloader.downloadFile(url);
        sources.add(file);
        externalSourceSize += file.getCode().length();
      } catch (IOException e) {
        observation.outcome("download_error");
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ErrorResponse(
                Response.Status.BAD_REQUEST.getStatusCode(),
                "Failed to download file: " + e.getMessage()))
            .type(MediaType.APPLICATION_JSON)
            .build();
      }
    }

    options.setEnvironment(CompilerOptions.Environment.BROWSER);
    options.setDependencyOptions(DependencyOptions.sortOnly());

    if (request.getLanguage() == null) {
      options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2021);
      options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2021);
    } else {
      if (request.getLanguage().getLanguageIn() != null) {
        options.setLanguageIn(CompilerOptions.LanguageMode.fromString(request.getLanguage().getLanguageIn()));
      }
      if (request.getLanguage().getLanguageOut() != null) {
        options.setLanguageOut(CompilerOptions.LanguageMode.fromString(request.getLanguage().getLanguageOut()));
      }
    }

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
        hash = cache.put(hash, compiler.toSource().getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
        observation.outcome("cache_error");
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ErrorResponse(
                Response.Status.BAD_REQUEST.getStatusCode(),
                "Failed to cache compiled code: " + e.getMessage()))
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
    observation.outcome(result.success ? "success" : "compiler_error");
    observation.sizes(stats.getOriginalSize(), stats.getCompiledSize());

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
  @Operation(
          summary = "Fetch compiled code",
          description = "Reads compiled code from cache using the provided hash",
          operationId = "read"
  )
  @Parameter(
          name        = "hash",
          description = "Hash‑code of the compiled JavaScript file",
          required    = true,
          in          = ParameterIn.PATH,
          schema      = @Schema(type = SchemaType.STRING)
  )
  @APIResponses(
          value = {
              @APIResponse(
                      responseCode = "200",
                      description  = "Javascript file (binary)",
                      content      = @Content(
                              mediaType = "application/octet-stream",
                              schema    = @Schema(type = SchemaType.STRING, format = "binary")
                      )
              ),
              @APIResponse(
                      responseCode = "400",
                      description  = "Invalid output file name",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "404",
                      description  = "Compiled code was not found",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "429",
                      description  = "Too many requests",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              ),
              @APIResponse(
                      responseCode = "500",
                      description  = "Internal server error",
                      content      = @Content(
                              mediaType = "application/json",
                              schema    = @Schema(implementation = ErrorResponse.class)
                      )
              )
          }
  )
  public Response read(
      @PathParam("hash") String hash,
      @Parameter(
          name = "filename",
          description = "Optional downloaded file name",
          in = ParameterIn.QUERY,
          schema = @Schema(type = SchemaType.STRING))
      @QueryParam("filename")
      @ValidFileName String filename) {
    byte[] bytes = new byte[0];
    try {
      bytes = cache.get(hash);
    } catch (Exception e) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new ErrorResponse(
              Response.Status.NOT_FOUND.getStatusCode(),
              "No compiled code found for hash " + hash))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    if (bytes == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new ErrorResponse(
              Response.Status.NOT_FOUND.getStatusCode(),
              "No compiled code found for hash " + hash))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
    String outputFileName = filename == null ? "default.js" : filename;
    return Response.ok(bytes)
        .header("Content-Disposition", "attachment; filename=\"" + outputFileName + "\"")
        .build();
  }
}
