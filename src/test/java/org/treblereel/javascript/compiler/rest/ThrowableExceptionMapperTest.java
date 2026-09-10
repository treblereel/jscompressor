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

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.domain.ErrorResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThrowableExceptionMapperTest {

  @Test
  public void unhandledErrorsReturnJsonWithoutInternalDetails() {
    ThrowableExceptionMapper mapper = new QuietThrowableExceptionMapper();

    Response response = mapper.toResponse(new IllegalStateException("test failure with internal details"));
    ErrorResponse entity = (ErrorResponse) response.getEntity();

    assertEquals(500, response.getStatus());
    assertEquals(500, entity.getStatus());
    assertEquals("Internal server error", entity.getError());
  }

  private static class QuietThrowableExceptionMapper extends ThrowableExceptionMapper {

    @Override
    protected void logUnhandledException(Throwable exception) {
    }
  }
}
