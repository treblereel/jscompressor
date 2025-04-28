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
