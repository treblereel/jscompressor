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

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class Statistics {

  private long originalSize;
  private long compiledSize;

  public Statistics() {}

  public Statistics(long originalSize, long compiledSize) {
    this.originalSize = originalSize;
    this.compiledSize = compiledSize;
  }

  public long getOriginalSize() {
    return originalSize;
  }

  public void setOriginalSize(long originalSize) {
    this.originalSize = originalSize;
  }

  public long getCompiledSize() {
    return compiledSize;
  }

  public void setCompiledSize(long compiledSize) {
    this.compiledSize = compiledSize;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Statistics that = (Statistics) o;
    return originalSize == that.originalSize && compiledSize == that.compiledSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(originalSize, compiledSize);
  }

  @Override
  public String toString() {
    return "Statistics{" + "originalSize=" + originalSize + ", compiledSize=" + compiledSize + '}';
  }
}
