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

package org.treblereel.javascript.compiler.downloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.google.javascript.jscomp.SourceFile;
import io.quarkus.runtime.annotations.StaticInitSafe;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FileDownloader {

    private final Set<String> buildin =
            new HashSet<>() {
                {
                    add("closure_library_base");
                    add("chrome_frame");
                    add("dojo");
                    add("ext_core");
                    add("jquery");
                    add("jquery_ui");
                    add("mootools");
                    add("prototype");
                    add("scriptaculous");
                    add("swfobject");
                    add("yui");
                    add("fonts_loader");
                }
            };

    @StaticInitSafe
    @ConfigProperty(name = "download.file.max-size", defaultValue = "1048576")
    long maxRequestSize;

    public SourceFile downloadFile(String fileUrl) throws IOException {
        if (buildin.contains(fileUrl)) {
            return getBuildinFile(fileUrl);
        }

        String source = downloadFileToTemp(fileUrl);
        String fileName = getFileNameFromUrl(fileUrl);
        return SourceFile.fromCode(fileName, source);
    }

    private SourceFile getBuildinFile(String fileUrl) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream =
                     classLoader.getResourceAsStream("META-INF/resources/buildin/" + fileUrl + ".js")) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + fileUrl);
            }

            String content =
                    IOUtils.toString(
                            new LimitedInputStream(inputStream, maxRequestSize), StandardCharsets.UTF_8);
            return SourceFile.fromCode(fileUrl + ".js", content);
        } catch (Exception e) {
            throw new IOException(
                    "Failed to load resource: "
                            + fileUrl
                            + (e.getMessage() != null ? " - " + e.getMessage() : ""));
        }
    }

    public String downloadFileToTemp(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxRequestSize) {
                throw new IOException(
                        "File is too large to download: "
                                + contentLength
                                + " bytes (max: "
                                + maxRequestSize
                                + " bytes)");
            }

            try (InputStream inputStream = connection.getInputStream()) {
                return IOUtils.toString(
                        new LimitedInputStream(inputStream, maxRequestSize), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IOException(
                        "Failed to load resource: "
                                + fileUrl
                                + (e.getMessage() != null ? " - " + e.getMessage() : ""));
            }
        } else {
            throw new IOException(
                    "Failed to download file: HTTP "
                            + connection.getResponseCode()
                            + " - "
                            + connection.getResponseMessage());
        }
    }

    private String getFileNameFromUrl(String fileUrl) {
        String[] parts = fileUrl.split("/");
        return parts[parts.length - 1];
    }
}
