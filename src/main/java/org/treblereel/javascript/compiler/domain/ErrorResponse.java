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

package org.treblereel.javascript.compiler.domain;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ErrorResponse", description = "ErrorResponse is a standard REST error response.")
public class ErrorResponse {

  @Schema(description = "HTTP response status code.")
  private int status;

  @Schema(description = "Human-readable error message.")
  private String error;

  public ErrorResponse() {
  }

  public ErrorResponse(int status, String error) {
    this.status = status;
    this.error = error;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }
}
