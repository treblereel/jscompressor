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

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "LanguageInOut",
        description = "LanguageInOut is a class that represents the input and output languages for JavaScript compilation.")
public class LanguageInOut {


  @Schema(description = "Input language. Possible values: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT, ECMASCRIPT_2015, ECMASCRIPT_2016, ECMASCRIPT_2017, ECMASCRIPT_2018, ECMASCRIPT_2019, ECMASCRIPT_2020, ECMASCRIPT_2021, ECMASCRIPT_NEXT and STABLE",
          enumeration = {"ECMASCRIPT3", "ECMASCRIPT5", "ECMASCRIPT5_STRICT", "ECMASCRIPT_2015", "ECMASCRIPT_2016", "ECMASCRIPT_2017", "ECMASCRIPT_2018", "ECMASCRIPT_2019", "ECMASCRIPT_2020", "ECMASCRIPT_2021", "ECMASCRIPT_NEXT", "STABLE"})
  private String languageIn;

  @Schema(description = "Output language. Possible values: ECMASCRIPT3, ECMASCRIPT5, ECMASCRIPT5_STRICT, ECMASCRIPT_2015, ECMASCRIPT_2016, ECMASCRIPT_2017, ECMASCRIPT_2018, ECMASCRIPT_2019, ECMASCRIPT_2020, ECMASCRIPT_2021, ECMASCRIPT_NEXT and STABLE",
          enumeration = {"ECMASCRIPT3", "ECMASCRIPT5", "ECMASCRIPT5_STRICT", "ECMASCRIPT_2015", "ECMASCRIPT_2016", "ECMASCRIPT_2017", "ECMASCRIPT_2018", "ECMASCRIPT_2019", "ECMASCRIPT_2020", "ECMASCRIPT_2021", "ECMASCRIPT_NEXT", "STABLE"})
  private String languageOut;

  public LanguageInOut() {
  }

  public LanguageInOut(String languageIn, String languageOut) {
    this.languageIn = languageIn;
    this.languageOut = languageOut;
  }

  public String getLanguageIn() {
    return languageIn;
  }

  public void setLanguageIn(String languageIn) {
    this.languageIn = languageIn;
  }

  public String getLanguageOut() {
    return languageOut;
  }

  public void setLanguageOut(String languageOut) {
    this.languageOut = languageOut;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LanguageInOut that = (LanguageInOut) o;
    return Objects.equals(languageIn, that.languageIn) && Objects.equals(languageOut, that.languageOut);
  }

  @Override
  public int hashCode() {
    return Objects.hash(languageIn, languageOut);
  }

  @Override
  public String toString() {
    return "LanguageInOut{" +
            "languageIn='" + languageIn + '\'' +
            ", languageOut='" + languageOut + '\'' +
            '}';
  }
}
