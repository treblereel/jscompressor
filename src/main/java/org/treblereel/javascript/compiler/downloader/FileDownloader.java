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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.google.javascript.jscomp.SourceFile;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
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

    @Inject
    Vertx vertx;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = vertx.createHttpClient(new HttpClientOptions().setVerifyHost(true));
    }

    @PreDestroy
    void close() {
        if (httpClient != null) {
            httpClient.close();
        }
    }

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
            DownloadResponse response = executeRequest(uri, resolveAllowedAddresses(uri));

            if (isRedirect(response.statusCode())) {
                String location = response.location();
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect response is missing Location header");
                }
                uri = validateExternalUri(uri.resolve(location).toString());
                continue;
            }

            if (response.statusCode() == 200) {
                return response.body();
            }

            String msg = String.format(
                    "Failed to download file: %s, %s - %s",
                    fileUrl,
                    response.statusCode(),
                    response.statusMessage());
            throw new IOException(msg);
        }

        throw new IOException("Too many redirects while downloading file: " + fileUrl);
    }

    private DownloadResponse executeRequest(URI uri, InetAddress[] addresses) throws IOException {
        ConnectionFailure lastFailure = null;
        for (InetAddress address : addresses) {
            try {
                return executeRequest(uri, address);
            } catch (ConnectionFailure e) {
                lastFailure = e;
            }
        }
        throw lastFailure == null
                ? new IOException("URL host cannot be resolved: " + uri.getHost())
                : lastFailure;
    }

    RequestOptions createRequestOptions(URI uri, InetAddress address) {
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }

        return new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setHost(uri.getHost())
                .setPort(port)
                .setSsl("https".equalsIgnoreCase(uri.getScheme()))
                .setURI(path)
                .setServer(SocketAddress.inetSocketAddress(port, address.getHostAddress()))
                .setConnectTimeout(serverConfig.downloadConnectTimeoutMs())
                .setIdleTimeout(serverConfig.downloadReadTimeoutMs())
                .setFollowRedirects(false);
    }

    DownloadResponse executeRequest(URI uri, InetAddress address) throws IOException {
        if (httpClient == null) {
            throw new IOException("HTTP client is not initialized");
        }

        HttpClientResponse response;
        try {
            response = httpClient.request(createRequestOptions(uri, address))
                    .compose(request -> request.send())
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading file: " + uri, e);
        } catch (ExecutionException e) {
            throw new ConnectionFailure("Failed to connect to resource: " + uri, e.getCause());
        }

        verifyConnectedAddress(response, address);
        int statusCode = response.statusCode();
        String statusMessage = response.statusMessage();
        String location = response.getHeader("Location");
        if (statusCode != 200) {
            closeConnection(response);
            return new DownloadResponse(statusCode, statusMessage, location, null);
        }

        long contentLength = parseContentLength(response);
        if (contentLength > serverConfig.downloadFileMaxSize()) {
            closeConnection(response);
            throw fileTooLarge(contentLength);
        }

        return new DownloadResponse(statusCode, statusMessage, location, readBody(response));
    }

    private String readBody(HttpClientResponse response) throws IOException {
        CompletableFuture<String> body = new CompletableFuture<>();
        long maxSize = serverConfig.downloadFileMaxSize();
        long[] size = {0};
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        response.pause();
        response.exceptionHandler(body::completeExceptionally);
        response.handler(buffer -> appendBody(response, buffer, output, size, maxSize, body));
        response.endHandler(ignored -> {
            if (!body.isDone()) {
                body.complete(output.toString(StandardCharsets.UTF_8));
            }
        });
        response.resume();

        try {
            return body.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeConnection(response);
            throw new IOException("Interrupted while reading downloaded file", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to read downloaded file", cause);
        }
    }

    private void appendBody(
            HttpClientResponse response,
            Buffer buffer,
            ByteArrayOutputStream output,
            long[] size,
            long maxSize,
            CompletableFuture<String> body) {
        if (body.isDone()) {
            return;
        }
        size[0] += buffer.length();
        if (size[0] > maxSize) {
            body.completeExceptionally(fileTooLarge(size[0]));
            closeConnection(response);
            return;
        }
        output.writeBytes(buffer.getBytes());
    }

    private long parseContentLength(HttpClientResponse response) {
        String value = response.getHeader("Content-Length");
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private IOException fileTooLarge(long size) {
        return new IOException(
                "File is too large to download: "
                        + size
                        + " bytes (max: "
                        + serverConfig.downloadFileMaxSize()
                        + " bytes)");
    }

    private void verifyConnectedAddress(HttpClientResponse response, InetAddress expected) throws IOException {
        String connectedAddress = response.request().connection().remoteAddress().hostAddress();
        InetAddress actual = InetAddress.getByName(connectedAddress);
        if (!Arrays.equals(expected.getAddress(), actual.getAddress())) {
            closeConnection(response);
            throw new IOException("Connection address changed after validation");
        }
    }

    private void closeConnection(HttpClientResponse response) {
        response.request().connection().close();
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

        return uri;
    }

    InetAddress[] resolveAllowedAddresses(URI uri) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        if (addresses.length == 0) {
            throw new IOException("URL host cannot be resolved: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IOException("URL host resolves to a blocked address: " + address.getHostAddress());
            }
        }
        return addresses;
    }

    private boolean isRedirect(int responseCode) {
        return responseCode == 301
                || responseCode == 302
                || responseCode == 303
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
                || isBlockedIpv4(bytes)
                || isBlockedIpv6(bytes);
    }

    private boolean isBlockedIpv4(byte[] bytes) {
        if (bytes.length != 4) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        return first == 0
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 192 && second == 88 && third == 99)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 240;
    }

    private boolean isBlockedIpv6(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        boolean globallyRoutablePrefix = (bytes[0] & 0xe0) == 0x20;
        boolean documentationPrefix = Byte.toUnsignedInt(bytes[0]) == 0x20
                && Byte.toUnsignedInt(bytes[1]) == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d
                && Byte.toUnsignedInt(bytes[3]) == 0xb8;
        return !globallyRoutablePrefix || documentationPrefix;
    }

    private String getFileNameFromUrl(String fileUrl) {
        String[] parts = fileUrl.split("/");
        return parts[parts.length - 1];
    }

    record DownloadResponse(
            int statusCode,
            String statusMessage,
            String location,
            String body) {
    }

    private static class ConnectionFailure extends IOException {

        private ConnectionFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
