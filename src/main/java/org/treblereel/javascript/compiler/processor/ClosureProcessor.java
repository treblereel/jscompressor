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

package org.treblereel.javascript.compiler.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DependencyOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.WarningLevel;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.treblereel.javascript.compiler.cache.FileCache;
import org.treblereel.javascript.compiler.domain.CompileRequest;
import org.treblereel.javascript.compiler.domain.CompileResponse;
import org.treblereel.javascript.compiler.domain.Statistics;
import org.treblereel.javascript.compiler.downloader.FileDownloader;
import org.treblereel.javascript.compiler.externs.ExternsProcessor;

@RequestScoped
public class ClosureProcessor {

    @Inject
    Logger logger;

    @Inject
    ExternsProcessor externsProcessor;

    @Inject
    FileDownloader fileDownloader;

    @Inject
    FileCache cache;

    public Uni<CompileResponse> process(CompileRequest request) {
        return Uni.createFrom().emitter(emitter -> {
            long start = System.currentTimeMillis();

            try {
                CompilerOptions options = buildOptions(request);
                AtomicLong externalSize = new AtomicLong(0);
                List<SourceFile> sources = downloadExternalSources(request, externalSize);
                sources.add(SourceFile.fromCode("input.js", request.getPayload()));

                Compiler compiler = compileSources(options, sources);
                CompileResponse response = buildResponse(request, compiler, externalSize.get());

                logStats(response, start);

                emitter.complete(response);
            } catch (Exception e) {
                emitter.fail(e);
            }
        });
    }

    private CompilerOptions buildOptions(CompileRequest request) {
        CompilerOptions options = new CompilerOptions();

        switch (request.getCompilationLevel()) {
            case "Simple" -> CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
            case "Advanced" -> CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
            case "Whitespace only" -> CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
            default -> CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
        }

        switch (request.getWarningLevel()) {
            case "QUIET" -> WarningLevel.QUIET.setOptionsForWarningLevel(options);
            case "VERBOSE" -> WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
            default -> WarningLevel.DEFAULT.setOptionsForWarningLevel(options);
        }

        options.setEnvironment(CompilerOptions.Environment.BROWSER);
        options.setDependencyOptions(DependencyOptions.sortOnly());

        if (request.getLanguage() != null) {
            if (request.getLanguage().getLanguageIn() != null) {
                options.setLanguageIn(CompilerOptions.LanguageMode.fromString(request.getLanguage().getLanguageIn()));
            }
            if (request.getLanguage().getLanguageOut() != null) {
                options.setLanguageOut(CompilerOptions.LanguageMode.fromString(request.getLanguage().getLanguageOut()));
            }
        } else {
            options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2021);
            options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2021);
        }

        if (request.getFormatting() != null) {
            options.setPrettyPrint(request.getFormatting().prettyPrint);
            options.setPrintInputDelimiter(request.getFormatting().printInputDelimiter);
        }

        options.setCheckTypes(true);
        return options;
    }

    private List<SourceFile> downloadExternalSources(CompileRequest request, AtomicLong collector) throws Exception {
        List<SourceFile> sources = new ArrayList<>();
        for (String url : request.getExternalScripts().getUrls()) {
            try {
                SourceFile file = fileDownloader.downloadFile(url);
                sources.add(file);
                collector.addAndGet(file.getCode().length());
            } catch (IOException e) {
                logger.errorf("Failed to download external script from %s: %s", url, e.getMessage());
                throw new Exception(String.format("Failed to download external script from %s: %s", url, e.getMessage()));
            }
        }
        return sources;
    }

    private Compiler compileSources(CompilerOptions options, List<SourceFile> sources) {
        Compiler compiler = new Compiler();
        compiler.initOptions(options);
        compiler.compile(externsProcessor.getExterns(), sources, options);
        return compiler;
    }

    private CompileResponse buildResponse(CompileRequest request, Compiler compiler, long externalSize) throws Exception {
        Result result = compiler.getResult();
        CompileResponse response = new CompileResponse();

        if (result.success) {
            String compiledCode = compiler.toSource();
            response.setCompiledCode(compiledCode);

            String hash = Long.toHexString(request.hashCode());
            try {
                cache.put(hash, compiledCode.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                String errorMessage = String.format("Failed to cache compiled code: %s", e.getMessage());
                logger.errorf(errorMessage);
                throw new Exception(errorMessage);
            }
            response.setDownloadId(hash);
        }

        response.setWarnings(result.warnings.stream().map(JSError::toString).toList());
        response.setErrors(result.errors.stream().map(JSError::toString).toList());

        Statistics stats = new Statistics();
        stats.setOriginalSize(request.getPayload().length() + externalSize);
        stats.setCompiledSize(response.getCompiledCode() != null ? response.getCompiledCode().length() : 0);
        response.setStatistics(stats);

        return response;
    }

    private void logStats(CompileResponse response, long start) {
        Statistics stats = response.getStatistics();
        logger.infof("compilation took %dms, payload size: %d bytes, compiled size: %d bytes",
                System.currentTimeMillis() - start,
                stats.getOriginalSize(),
                stats.getCompiledSize());
    }
}
