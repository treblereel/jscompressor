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

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.jboss.logging.Logger;
import org.treblereel.javascript.compiler.cache.FileCache;
import org.treblereel.javascript.compiler.config.ServerConfig;
import org.treblereel.javascript.compiler.domain.CompileRequest;
import org.treblereel.javascript.compiler.domain.CompileResponse;
import org.treblereel.javascript.compiler.domain.db.Script;
import org.treblereel.javascript.compiler.downloader.FileDownloader;
import org.treblereel.javascript.compiler.externs.ExternsProcessor;
import org.treblereel.javascript.compiler.processor.ClosureProcessor;

@Path("/compile")
@OpenAPIDefinition(
        info = @Info(
                title = "JavaScript Compiler API",
                version = "0.5",
                description = "API for compiling JavaScript code using Google Closure Compiler"
        ),
        servers = @Server(url = "https://jscompressor.treblereel.dev/", description = "Production server")
)
public class CompilerResource {

    @Inject
    ExternsProcessor externsProcessor;

    @Inject
    Logger logger;

    @Inject
    FileDownloader fileDownloader;

    @Inject
    FileCache cache;

    @Inject
    ServerConfig serverConfig;

    @Inject
    ClosureProcessor closureProcessor;

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
    //@RateLimit(value = 10, window = 1, windowUnit = ChronoUnit.SECONDS)
    @Timeout(value = 120, unit = ChronoUnit.SECONDS)
    @Timed(value = "compile", extraTags = {"method", "POST"}, description = "Time taken to compile")
    @Counted(value = "compile", extraTags = {"method", "POST"}, description = "Number of compilations")
    @Operation(
            summary = "Compile JavaScript code",
            description = "Compiles JavaScript code using Google Closure Compiler",
            operationId = "compileJavaScript"
    )
    @RequestBody(
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CompileRequest.class)
            )
    )
    @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CompileResponse.class)
            )
    )
    @ActivateRequestContext
    public CompletionStage<Response> compile(@Valid CompileRequest request, @Context io.vertx.core.http.HttpServerRequest req) {
        logger.info("received request from " + req.remoteAddress().host());
        return closureProcessor.process(request)
                .onItem().transform(response -> Response.ok(response).build())
                .onFailure().recoverWithItem(throwable -> {
                    logger.error("Compilation failed", throwable);
                    CompileResponse errorResponse = new CompileResponse();
                    errorResponse.setErrors(List.of(throwable.getMessage()));
                    return Response.status(Response.Status.BAD_REQUEST)
                            .type(MediaType.APPLICATION_JSON)
                            .entity(errorResponse)
                            .build();
                }).subscribeAsCompletionStage();

    }

  @GET
  @Path("/{hash}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Bulkhead(value = 10, waitingTaskQueue = 5)
  @Timeout(value = 20, unit = ChronoUnit.SECONDS)
  @Timed(value = "read", extraTags = {"method", "GET"}, description = "Time taken to read")
  @Counted(value = "read", extraTags = {"method", "GET"}, description = "Number of reads")
  @Operation(
          summary = "Fetch compiled code",
          description = "Reads compiled code from cache using the provided hash",
          operationId = "readCompiledCode"
  )
  @Parameter(
          name        = "hash",
          description = "Hash‑code of the compiled JavaScript file",
          required    = true,
          in          = ParameterIn.PATH,
          schema      = @Schema(type = SchemaType.STRING)
  )
  @APIResponse(
          responseCode = "200",
          description  = "Javascript file (binary)",
          content      = @Content(
                  mediaType = "application/octet-stream",
                  schema    = @Schema(type = SchemaType.STRING, format = "binary")
          )
  )
  @Transactional
  public Response read(@PathParam("hash") String hashName) {
    Optional<Script> scriptOptional = Script.find("hash", hashName).firstResultOptional();
    if(scriptOptional.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND)
              .entity("No compiled code found for hash " + hashName)
              .build();
    }
    Script script = scriptOptional.get();
    String fileName = script.getFilename();

    byte[] bytes;
    try {
      bytes = cache.get(fileName);
    } catch (Exception e) {
      return Response.status(Response.Status.NOT_FOUND)
              .entity("No compiled code found for hash " + fileName)
              .build();
    }
    if (bytes == null) {
      return Response.status(Response.Status.NOT_FOUND)
              .entity("No compiled code found for hash " + fileName)
              .build();
    }
    String result = String.format("attachment; filename=\"%s.js\"", hashName);
    return Response.ok(bytes)
            .header("Content-Disposition", result)
            .build();
  }
}
