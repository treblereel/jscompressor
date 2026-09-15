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

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import io.vertx.ext.web.RoutingContext;
import org.treblereel.javascript.compiler.domain.ErrorResponse;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class RateLimitFilter implements ContainerRequestFilter {

  @Inject
  RateLimiter rateLimiter;

  @Inject
  RoutingContext routingContext;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (!isCompileEndpoint(requestContext)) {
      return;
    }
    if (rateLimiter.isAllowed(remoteHost())) {
      return;
    }
    requestContext.abortWith(
            Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(new ErrorResponse(
                        Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                        "Rate limit exceeded. Please try again later."))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
  }

  private boolean isCompileEndpoint(ContainerRequestContext requestContext) {
    String path = requestContext.getUriInfo().getPath();
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    return path.equals("compile") || path.startsWith("compile/");
  }

  private String remoteHost() {
    if (routingContext == null
        || routingContext.request() == null
        || routingContext.request().remoteAddress() == null) {
      return "unknown";
    }
    return routingContext.request().remoteAddress().host();
  }
}
