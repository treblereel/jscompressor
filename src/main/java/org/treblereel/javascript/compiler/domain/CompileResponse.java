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

import java.util.List;
import java.util.Objects;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CompileResponse {

  private String compiledCode;

  private String downloadId;
  private List<String> warnings;
  private List<String> errors;
  private Statistics statistics;

  public CompileResponse() {}

  public CompileResponse(
      String compiledCode,
      String downloadId,
      List<String> warnings,
      List<String> errors,
      Statistics statistics) {
    this.compiledCode = compiledCode;
    this.downloadId = downloadId;
    this.warnings = warnings;
    this.errors = errors;
    this.statistics = statistics;
  }

  public String getCompiledCode() {
    return compiledCode;
  }

  public void setCompiledCode(String compiledCode) {
    this.compiledCode = compiledCode;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  public Statistics getStatistics() {
    return statistics;
  }

  public void setStatistics(Statistics statistics) {
    this.statistics = statistics;
  }

  public String getDownloadId() {
    return downloadId;
  }

  public void setDownloadId(String downloadId) {
    this.downloadId = downloadId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CompileResponse that = (CompileResponse) o;
    return Objects.equals(compiledCode, that.compiledCode)
        && Objects.equals(downloadId, that.downloadId)
        && Objects.equals(warnings, that.warnings)
        && Objects.equals(errors, that.errors)
        && Objects.equals(statistics, that.statistics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compiledCode, downloadId, warnings, errors, statistics);
  }

  @Override
  public String toString() {
    return "CompileResponse{"
        + "compiledCode='"
        + compiledCode
        + '\''
        + ", downloadId='"
        + downloadId
        + '\''
        + ", warnings="
        + warnings
        + ", errors="
        + errors
        + ", statistics="
        + statistics
        + '}';
  }
}
