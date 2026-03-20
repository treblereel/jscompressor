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

import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.treblereel.javascript.compiler.validation.MaxPayloadSize;
import org.treblereel.javascript.compiler.validation.ValidFileName;


@Schema(name = "CompileRequest",
        description = "CompileRequest is a request to compile JavaScript code.")
public class CompileRequest {

  @Schema(description = "Compilation level. Possible values: WHITESPACE, SIMPLE, ADVANCED.", enumeration = "WHITESPACE, SIMPLE, ADVANCED")
  private String compilationLevel;
  @Schema(description = "Warning level. Possible values: QUIET, DEFAULT, VERBOSE.", enumeration = "QUIET, DEFAULT, VERBOSE")
  private String warningLevel;

  @NotNull
  @ValidFileName
  @Schema(description = "Output file name. Must be a valid file name.")
  private String outputFileName;

  @Schema(description = "Language input and output",
          implementation = LanguageInOut.class)
  private LanguageInOut language;

  @MaxPayloadSize
  @Schema(description = "JavaScript code to be compiled.",
          type = SchemaType.STRING)
  private String payload;

  @Schema(description = "Formatting options.", implementation = Formatting.class)
  private Formatting formatting;

  @Valid
  @Schema(description = "External scripts to be included in the compilation.",
          implementation = ExternalScripts.class)
  private ExternalScripts externalScripts;

  public CompileRequest() {
  }

  public String getCompilationLevel() {
    return compilationLevel;
  }

  public void setCompilationLevel(String compilationLevel) {
    this.compilationLevel = compilationLevel;
  }

  public String getWarningLevel() {
    return warningLevel;
  }

  public void setWarningLevel(String warningLevel) {
    this.warningLevel = warningLevel;
  }

  public String getOutputFileName() {
    return outputFileName;
  }

  public void setOutputFileName(String outputFileName) {
    this.outputFileName = outputFileName;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Formatting getFormatting() {
    return formatting;
  }

  public void setFormatting(Formatting formatting) {
    this.formatting = formatting;
  }

  public ExternalScripts getExternalScripts() {
    return externalScripts;
  }

  public void setExternalScripts(ExternalScripts externalScripts) {
    this.externalScripts = externalScripts;
  }

  public LanguageInOut getLanguage() {
    return language;
  }

  public void setLanguage(LanguageInOut language) {
    this.language = language;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CompileRequest that = (CompileRequest) o;
    return Objects.equals(compilationLevel, that.compilationLevel) && Objects.equals(warningLevel, that.warningLevel) && Objects.equals(outputFileName, that.outputFileName) && Objects.equals(language, that.language) && Objects.equals(payload, that.payload) && Objects.equals(formatting, that.formatting) && Objects.equals(externalScripts, that.externalScripts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compilationLevel, warningLevel, outputFileName, language, payload, formatting, externalScripts);
  }

  @Override
  public String toString() {
    return "CompileRequest{" +
            "compilationLevel='" + compilationLevel + '\'' +
            ", warningLevel='" + warningLevel + '\'' +
            ", outputFileName='" + outputFileName + '\'' +
            ", language='" + language + '\'' +
            ", payload='" + payload + '\'' +
            ", formatting=" + formatting +
            ", externalScripts=" + externalScripts +
            '}';
  }
}
