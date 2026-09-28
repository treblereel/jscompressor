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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.google.javascript.jscomp.SourceFile;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
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
        httpClient = vertx.createHttpClient(clientOptions());
    }

    HttpClientOptions clientOptions() {
        return new HttpClientOptions()
                .setVerifyHost(true).setTryUseCompression(false).setKeepAlive(false);
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

        String source = downloadSource(fileUrl);
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
                            + (e.getMessage() != null ? " - " + e.getMessage() : ""), e);
        }
    }

    public String downloadSource(String fileUrl) throws IOException {
        return awaitDownload(startDownload(fileUrl)).body();
    }

    Download startDownload(String fileUrl) throws IOException {
        URI uri = validateExternalUri(fileUrl);
        Download operation = new Download();
        operation.context.runOnContext(ignored -> {
            operation.start();
            operation.resolve(uri, 0);
        });
        return operation;
    }

    /** Retained for callers of the previous API; downloads have always stayed in memory. */
    @Deprecated
    public String downloadFileToTemp(String fileUrl) throws IOException {
        return downloadSource(fileUrl);
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
        Download operation = new Download();
        operation.context.runOnContext(ignored -> {
            operation.start();
            operation.request(uri, new InetAddress[]{address}, 0, 0);
        });
        return awaitDownload(operation);
    }

    private DownloadResponse awaitDownload(Download operation) throws IOException {
        try {
            return operation.result.future().toCompletionStage().toCompletableFuture()
                    .get(Math.max(0, operation.deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            IOException failure = new IOException("Interrupted while downloading file", e);
            operation.cancel(failure);
            Thread.currentThread().interrupt();
            throw failure;
        } catch (TimeoutException e) {
            IOException failure = new IOException("Download deadline exceeded", e);
            operation.cancel(failure);
            throw failure;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("Failed to read downloaded file", cause);
        }
    }

    // All mutable state and response handlers belong to this one Vert.x context.
    // Only the caller waits; the event loop never blocks on a future or DNS.
    final class Download {
        private final Context context;
        private final Promise<DownloadResponse> result = Promise.promise();
        private final long deadline;
        private HttpClientRequest activeRequest;
        private long timer = -1;
        private volatile IOException cancellation;

        Future<DownloadResponse> future() {
            return result.future();
        }

        private Download() throws IOException {
            if (Context.isOnEventLoopThread()) {
                throw new IllegalStateException("Blocking download must run on a worker thread");
            }
            if (httpClient == null) {
                throw new IOException("HTTP client is not initialized");
            }
            if (serverConfig.downloadTimeoutMs() <= 0) {
                throw new IllegalStateException("Download timeout must be positive");
            }
            context = vertx.getOrCreateContext();
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(serverConfig.downloadTimeoutMs());
        }

        private void start() {
            if (stopped()) {
                return;
            }
            long remainingMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            timer = vertx.setTimer(remainingMs, ignored ->
                    fail(new IOException("Download deadline exceeded")));
        }

        private void cancel(IOException cause) {
            cancellation = cause;
            context.runOnContext(ignored -> fail(cause));
        }

        private boolean stopped() {
            if (cancellation != null) {
                fail(cancellation);
            }
            if (!result.future().isComplete() && System.nanoTime() >= deadline) {
                fail(new IOException("Download deadline exceeded"));
            }
            return result.future().isComplete();
        }

        private void fail(Throwable cause) {
            if (result.tryFail(cause)) {
                vertx.cancelTimer(timer);
                if (activeRequest != null) {
                    activeRequest.reset();
                    activeRequest = null;
                }
            }
        }

        private void resolve(URI uri, int redirects) {
            if (stopped()) {
                return;
            }
            // A slow system resolver cannot be forcibly cancelled, but its late result
            // is ignored and can never initiate a connection after the deadline.
            vertx.executeBlocking(() -> resolveAllowedAddresses(uri), false).onComplete(resolved -> {
                if (stopped()) {
                    return;
                }
                if (resolved.failed()) {
                    fail(resolved.cause());
                } else if (resolved.result().length == 0) {
                    fail(new IOException("URL host cannot be resolved: " + uri.getHost()));
                } else {
                    request(uri, resolved.result(), 0, redirects);
                }
            });
        }

        private void request(URI uri, InetAddress[] addresses, int index, int redirects) {
            if (stopped()) {
                return;
            }
            httpClient.request(createRequestOptions(uri, addresses[index])).onComplete(created -> {
                if (stopped()) {
                    if (created.succeeded()) {
                        created.result().reset();
                    }
                    return;
                }
                if (created.failed()) {
                    connectionFailed(uri, addresses, index, redirects, created.cause());
                    return;
                }
                activeRequest = created.result();
                activeRequest.putHeader("Accept-Encoding", "identity");
                activeRequest.send().onComplete(sent -> {
                    if (stopped()) {
                        if (sent.succeeded()) {
                            closeConnection(sent.result());
                        }
                        return;
                    }
                    if (sent.failed()) {
                        connectionFailed(uri, addresses, index, redirects, sent.cause());
                        return;
                    }
                    // Register every body handler before returning control to Vert.x.
                    receive(uri, addresses[index], redirects, sent.result());
                });
            });
        }

        private void connectionFailed(URI uri, InetAddress[] addresses, int index, int redirects, Throwable cause) {
            if (activeRequest != null) {
                activeRequest.reset();
                activeRequest = null;
            }
            if (index + 1 < addresses.length) {
                request(uri, addresses, index + 1, redirects);
            } else {
                fail(new IOException("Failed to connect to resource: " + uri, cause));
            }
        }

        private void receive(URI uri, InetAddress address, int redirects, HttpClientResponse response) {
            try {
                // Rejected/redirect bodies are intentionally closed, not consumed.
                response.exceptionHandler(ignored -> {});
                verifyConnectedAddress(response, address);
                int status = response.statusCode();
                if (status != 200) {
                    closeConnection(response);
                    activeRequest = null;
                    if (!isRedirect(status)) {
                        throw new IOException("HTTP " + status + " while downloading " + uri);
                    }
                    if (redirects >= serverConfig.downloadMaxRedirects()) {
                        throw new IOException("Too many redirects while downloading file");
                    }
                    String location = response.getHeader("Location");
                    if (location == null || location.isBlank()) {
                        throw new IOException("Redirect response is missing Location header");
                    }
                    URI next;
                    try {
                        next = validateExternalUri(uri.resolve(location).toString());
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Invalid redirect Location", e);
                    }
                    resolve(next, redirects + 1);
                    return;
                }
                String encoding = response.getHeader("Content-Encoding");
                if (encoding != null && !"identity".equalsIgnoreCase(encoding.trim())) {
                    throw new IOException("Unsupported Content-Encoding: " + encoding);
                }
                long length = parseContentLength(response);
                if (length > serverConfig.downloadFileMaxSize()) {
                    throw fileTooLarge(length);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                long[] size = {0};
                response.exceptionHandler(cause -> fail(new IOException("Failed to read downloaded file", cause)));
                response.handler(buffer -> {
                    if (stopped()) {
                        return;
                    }
                    size[0] += buffer.length();
                    if (size[0] > serverConfig.downloadFileMaxSize()) {
                        fail(fileTooLarge(size[0]));
                    } else {
                        output.writeBytes(buffer.getBytes());
                    }
                });
                response.endHandler(ignored -> {
                    if (!stopped()) {
                        activeRequest = null;
                        vertx.cancelTimer(timer);
                        result.tryComplete(new DownloadResponse(status, response.statusMessage(), null,
                                output.toString(StandardCharsets.UTF_8)));
                    }
                });
            } catch (IOException | RuntimeException e) {
                closeConnection(response);
                fail(e);
            }
        }
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
        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) {
            throw new IOException("URL port must be between 1 and 65535");
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
        String path = URI.create(fileUrl).getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? "external.js" : name;
    }

    record DownloadResponse(
            int statusCode,
            String statusMessage,
            String location,
            String body) {
    }

}
