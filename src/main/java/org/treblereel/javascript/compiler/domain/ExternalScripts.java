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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.treblereel.javascript.compiler.validation.MaxExternalUrls;

@Schema(
        name = "ExternalScripts",
        description =
                "ExternalScripts is a class that represents the external scripts to be included in the compilation.")
public class ExternalScripts {

  @MaxExternalUrls
  @Schema(
          description = "List of external scripts to be included in the compilation.",
          type = SchemaType.ARRAY,
          implementation = String.class)
  private List<String> urls;

  public ExternalScripts() {
    urls = new ArrayList<>();
  }

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(List<String> urls) {
    this.urls = urls;
  }

  @Override
  public String toString() {
    return "ExternalScripts{" + "urls=" + urls + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExternalScripts that = (ExternalScripts) o;
    return Objects.equals(urls, that.urls);
  }

  @Override
  public int hashCode() {
    return Objects.hash(urls);
  }
}
