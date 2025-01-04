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

public class Formatting {

  public boolean prettyPrint;
  public boolean printInputDelimiter;

  public Formatting() {}

  public Formatting(boolean prettyPrint, boolean printInputDelimiter) {
    this.prettyPrint = prettyPrint;
    this.printInputDelimiter = printInputDelimiter;
  }

  public boolean isPrettyPrint() {
    return prettyPrint;
  }

  public void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }

  public boolean isPrintInputDelimiter() {
    return printInputDelimiter;
  }

  public void setPrintInputDelimiter(boolean printInputDelimiter) {
    this.printInputDelimiter = printInputDelimiter;
  }

  public String toString() {
    return "Formatting{"
        + "prettyPrint="
        + prettyPrint
        + ", printInputDelimiter="
        + printInputDelimiter
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Formatting that = (Formatting) o;
    return prettyPrint == that.prettyPrint && printInputDelimiter == that.printInputDelimiter;
  }

  @Override
  public int hashCode() {
    return Objects.hash(prettyPrint, printInputDelimiter);
  }
}
