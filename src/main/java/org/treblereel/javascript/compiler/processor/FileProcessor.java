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

package org.treblereel.javascript.compiler.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.jboss.logging.Logger;
import org.treblereel.javascript.compiler.cache.FileCache;
import org.treblereel.javascript.compiler.domain.db.Script;

@ApplicationScoped
public class FileProcessor {

  @Inject
  Logger logger;

  @Inject
  FileCache cache;

  public Uni<Response> fetchFileByFilename(String filename) {
    return Script
            .find("filename", filename)
            .<Script>firstResult()
            .onItem()
            .transformToUni(script -> {
              if (script == null) {
                return Uni.createFrom().item(
                        Response.status(Response.Status.NOT_FOUND)
                                .entity("No compiled code found for hash " + filename)
                                .build()
                );
              }
              String fileName = script.getFilename();
              String contentDisposition =
                      "attachment; filename=\"" + fileName + "\"";

              return Uni.createFrom()
                      .item(() -> {
                        try {
                          return cache.get(fileName);
                        } catch (Exception e) {
                          logger.error("Error reading file from cache: " + fileName, e);
                          return null;
                        }

                      })
                      .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                      .onItem().transform(bytes -> {
                        if (bytes == null) {
                          return Response.status(Response.Status.NOT_FOUND)
                                  .entity("No compiled code found for file " + fileName)
                                  .build();
                        }
                        return Response.ok(bytes)
                                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                .build();
                      })
                      .onFailure().recoverWithItem(ex ->
                              Response.status(Response.Status.NOT_FOUND)
                                      .entity("Error reading file " + fileName)
                                      .build()
                      );
            });
  }


}
