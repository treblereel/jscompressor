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

import java.util.Set;

import jakarta.validation.ConstraintValidator;

import com.google.javascript.jscomp.CompilerOptions;

public class JsLanguageLevelValidator implements ConstraintValidator<JsLanguageLevel, String> {

    private Set<CompilerOptions.LanguageMode> languageModes = Set.of(CompilerOptions.LanguageMode.ECMASCRIPT3,
            CompilerOptions.LanguageMode.ECMASCRIPT5,
            CompilerOptions.LanguageMode.ECMASCRIPT5_STRICT,
            CompilerOptions.LanguageMode.ECMASCRIPT_2015,
            CompilerOptions.LanguageMode.ECMASCRIPT_2016,
            CompilerOptions.LanguageMode.ECMASCRIPT_2017,
            CompilerOptions.LanguageMode.ECMASCRIPT_2018,
            CompilerOptions.LanguageMode.ECMASCRIPT_2019,
            CompilerOptions.LanguageMode.ECMASCRIPT_2020,
            CompilerOptions.LanguageMode.ECMASCRIPT_2021,
            CompilerOptions.LanguageMode.ECMASCRIPT_NEXT,
            CompilerOptions.LanguageMode.STABLE,
            CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);

    @Override
    public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        // Add your validation logic here
        return languageModes.contains(CompilerOptions.LanguageMode.fromString(value));
    }
}
