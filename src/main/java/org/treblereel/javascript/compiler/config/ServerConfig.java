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

package org.treblereel.javascript.compiler.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@StaticInitSafe
@ConfigMapping(prefix = "server")
public interface ServerConfig {

  @ConfigProperty(defaultValue = "102400")
  long downloadFileMaxSize();

  @ConfigProperty(defaultValue = "102400")
  long cacheMaxSize();

  @ConfigProperty(defaultValue = "10")
  long downloadUrlsPreRequest();

  @ConfigProperty(defaultValue = "cache")
  String cacheLocation();

}
