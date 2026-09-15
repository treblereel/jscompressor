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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.treblereel.javascript.compiler.config.ServerConfig;
import org.treblereel.javascript.compiler.domain.ErrorResponse;

@Provider
@ApplicationScoped
public class RequestSizeFilter implements ContainerRequestFilter {

  @Inject
  jakarta.inject.Provider<ServerConfig> serverConfig;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String contentLengthHeader = requestContext.getHeaderString("Content-Length");
    ServerConfig config = serverConfig.get();
    long maxSize = bodyLimit(config.requestBodyMaxSize(), config.downloadFileMaxSize());
    if (contentLengthHeader != null) {
      try {
        long contentLength = Long.parseLong(contentLengthHeader);
        if (contentLength > maxSize) {
          requestContext.abortWith(
                  Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                          .entity(new ErrorResponse(
                              Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(),
                              "Request size exceeds the maximum limit of "
                                  + maxSize
                                  + " bytes"))
                          .type(MediaType.APPLICATION_JSON)
                          .build());
          return;
        }
      } catch (NumberFormatException e) {
        requestContext.abortWith(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ErrorResponse(
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            "Invalid Content-Length header."))
                        .type(MediaType.APPLICATION_JSON)
                        .build());
        return;
      }
    }

    if (requestContext.hasEntity()) {
      byte[] entity = readEntity(requestContext.getEntityStream(), maxSize);
      if (entity == null) {
        requestContext.abortWith(
                Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                        .entity(new ErrorResponse(
                            Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(),
                            "Request size exceeds the maximum limit of "
                                + maxSize
                                + " bytes"))
                        .type(MediaType.APPLICATION_JSON)
                        .build());
      } else {
        requestContext.setEntityStream(new ByteArrayInputStream(entity));
      }
    }
  }

  static long bodyLimit(long configuredLimit, long payloadLimit) {
    if (configuredLimit < 0 || payloadLimit < 0) {
      throw new IllegalArgumentException("Request size limits must not be negative");
    }
    return configuredLimit == 0
        ? Math.addExact(Math.multiplyExact(payloadLimit, 6), 65536)
        : configuredLimit;
  }

  static byte[] readEntity(InputStream input, long maxSize) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long remaining = maxSize;
    while (remaining > 0) {
      int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
      if (count == -1) {
        return output.toByteArray();
      }
      output.write(buffer, 0, count);
      remaining -= count;
    }
    return input.read() == -1 ? output.toByteArray() : null;
  }
}
