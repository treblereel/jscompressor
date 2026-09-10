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
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.google.javascript.jscomp.SourceFile;
import org.apache.commons.io.IOUtils;
import org.treblereel.javascript.compiler.config.ServerConfig;

@ApplicationScoped
public class FileDownloader {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

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

    @Inject
    ServerConfig serverConfig;

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
                            new LimitedInputStream(inputStream, serverConfig.downloadFileMaxSize()), StandardCharsets.UTF_8);
            return SourceFile.fromCode(fileUrl + ".js", content);
        } catch (Exception e) {
            throw new IOException(
                    "Failed to load resource: "
                            + fileUrl
                            + (e.getMessage() != null ? " - " + e.getMessage() : ""));
        }
    }

    public String downloadFileToTemp(String fileUrl) throws IOException {
        URI uri = validateExternalUri(fileUrl);

        for (int redirectCount = 0; redirectCount <= serverConfig.downloadMaxRedirects(); redirectCount++) {
            HttpURLConnection connection = openConnection(uri);
            int responseCode = connection.getResponseCode();

            if (isRedirect(responseCode)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect response is missing Location header");
                }
                uri = validateExternalUri(uri.resolve(location).toString());
                continue;
            }

            if (responseCode == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                if (contentLength > serverConfig.downloadFileMaxSize()) {
                    connection.disconnect();
                    throw new IOException(
                            "File is too large to download: "
                                    + contentLength
                                    + " bytes (max: "
                                    + serverConfig.downloadFileMaxSize()
                                    + " bytes)");
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    return IOUtils.toString(
                            new LimitedInputStream(inputStream, serverConfig.downloadFileMaxSize()), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IOException(
                            "Failed to load resource: "
                                    + fileUrl
                                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
                } finally {
                    connection.disconnect();
                }
            }

            String msg = String.format("Failed to download file: %s, %s - %s", fileUrl, responseCode, connection.getResponseMessage());
            connection.disconnect();
            throw new IOException(msg);
        }

        throw new IOException("Too many redirects while downloading file: " + fileUrl);
    }

    private HttpURLConnection openConnection(URI uri) throws IOException {
        validateResolvedAddresses(uri);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(serverConfig.downloadConnectTimeoutMs());
        connection.setReadTimeout(serverConfig.downloadReadTimeoutMs());
        connection.setRequestMethod("GET");
        return connection;
    }

    private URI validateExternalUri(String fileUrl) throws IOException {
        URI uri;
        try {
            uri = new URI(fileUrl).normalize();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL: " + fileUrl, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IOException("Unsupported URL scheme: " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IOException("URL host is required");
        }
        if (uri.getUserInfo() != null) {
            throw new IOException("URL user info is not allowed");
        }

        validateResolvedAddresses(uri);
        return uri;
    }

    private void validateResolvedAddresses(URI uri) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        if (addresses.length == 0) {
            throw new IOException("URL host cannot be resolved: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IOException("URL host resolves to a blocked address: " + address.getHostAddress());
            }
        }
    }

    private boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    private boolean isBlockedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(bytes);
    }

    private boolean isUniqueLocalIpv6(byte[] bytes) {
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private String getFileNameFromUrl(String fileUrl) {
        String[] parts = fileUrl.split("/");
        return parts[parts.length - 1];
    }
}
