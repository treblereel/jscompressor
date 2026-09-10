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

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;
import org.treblereel.javascript.compiler.domain.ErrorResponse;

@Provider
public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {

  @Inject
  Logger logger = Logger.getLogger(ThrowableExceptionMapper.class);

  @Override
  public Response toResponse(Throwable exception) {
    if (exception instanceof WebApplicationException webApplicationException
        && webApplicationException.getResponse() != null) {
      int status = webApplicationException.getResponse().getStatus();
      return Response.status(status)
          .entity(new ErrorResponse(status, webApplicationException.getMessage()))
          .type(MediaType.APPLICATION_JSON)
          .build();
    }

    logUnhandledException(exception);
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(new ErrorResponse(
            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
            "Internal server error"))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  protected void logUnhandledException(Throwable exception) {
    logger.error("Unhandled REST exception", exception);
  }
}
