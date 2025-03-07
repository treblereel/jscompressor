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

package org.treblereel.javascript.compiler.validation;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.treblereel.javascript.compiler.config.ServerConfig;

@ApplicationScoped
public class MaxExternalUrlsValidator
    implements ConstraintValidator<MaxExternalUrls, List<String>> {

  @Inject ServerConfig serverConfig;

  @Override
  public boolean isValid(List<String> urls, ConstraintValidatorContext context) {
    if (urls == null) {
      return true;
    }
    return urls.size() <= serverConfig.downloadUrlsPreRequest();
  }
}
