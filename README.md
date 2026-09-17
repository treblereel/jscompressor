[![CI](https://github.com/treblereel/jscompressor/actions/workflows/ci.yml/badge.svg)](https://github.com/treblereel/jscompressor/actions/workflows/ci.yml)
[![Docker Pulls](https://img.shields.io/docker/pulls/treblereel/jscompressor.svg)](https://hub.docker.com/r/treblereel/jscompressor)
[![Docker Image Version (latest by date)](https://img.shields.io/docker/v/treblereel/jscompressor?sort=semver)](https://hub.docker.com/r/treblereel/jscompressor/tags)


## JSCompressor
A web application serving as both a user-friendly web interface and a REST API for Google's Closure Compiler.

## The application is available at https://jscompressor.treblereel.dev/

## Getting Started

### Prerequisites
- java 21 (GraalVM for native image)
- maven 3.8.3
- or Docker/Podman (optional)

### Installation

#### Build Java

1. Clone the repository
2. Run `mvn clean package`
3. Run `java -jar ./target/quarkus-app/quarkus-run.jar`

The repository contains prebuilt, self-hosted UI assets, so Java and container builds do not require Node.js.
When changing UI styles or upgrading Alpine.js/Tailwind CSS, install Node.js 20 or newer and regenerate the
committed assets with:

```shell
npm ci
npm run build
```

#### Build Native Image
1. Clone the repository
2. Run `mvn clean package -Pnative`
3. Run `./target/jscompressor-0.1-runner`

#### Docker/Podman

The native image is available on [Docker Hub](https://hub.docker.com/r/treblereel/jscompressor).

Run the published image:

```shell
docker pull treblereel/jscompressor:latest
docker run --detach --name jscompressor \
  --publish 8080:8080 \
  --volume jscompressor-cache:/volume \
  treblereel/jscompressor:latest
```

Build the native container locally:

```shell
mvn clean package -Pnative
docker build --file src/main/docker/Dockerfile.native --tag jscompressor .
docker run --rm --publish 8080:8080 --volume jscompressor-cache:/volume jscompressor
```

### Publish the native image

The `Publish native image` GitHub Actions workflow publishes `linux/amd64` images to
`docker.io/treblereel/jscompressor`. Configure these repository secrets before running it:

- `DOCKERHUB_USERNAME` - Docker Hub username.
- `DOCKERHUB_TOKEN` - Docker Hub access token with permission to push the repository.

Pushing a tag matching `v*` or `r*` publishes the Git tag, `sha-<commit>`, and `latest` image tags.
The workflow can also be started manually with a custom image tag and an optional `latest` update.
It builds the native executable in the Quarkus Linux builder container, runs the native integration test,
smoke-tests the final container, and rejects fixable high or critical vulnerabilities before logging in and
pushing it. Published images include SBOM and build provenance attestations.

Note: You should be familiar with such topics like Docker root/rootless containers, selinux and such topics. For
instance, if you get `permission denied` error, you should check the selinux context of the volume. That is why I prefer 
rootless Podman containers, that configure the context automatically.

Tips: If you are a mac user, check you build the native image for Linux. (docs docker buildx)

Tips: https://quarkus.io/guides/container-image

### Configuration

The application can be configured using the following environment variables:

- `MAX_DOWNLOAD_FILE_SIZE` - Sets the maximum file/script size that can be uploaded to the server. Default is 1048576 bytes (1MB).
- `MAX_REQUEST_BODY_SIZE` (`server.request-body-max-size`) - Maximum complete JSON request size in bytes. Default is `0`: automatically uses six times `server.download-file-max-size` plus 65536 bytes for JSON escaping and request metadata. A positive value sets an explicit body limit. Requests above this limit return JSON HTTP 413, including chunked requests. The decoded payload still uses `server.download-file-max-size`; an oversized payload within the body limit returns HTTP 400.
- `MAX_DOWNLOAD_URLS_PER_REQUEST` - Sets the maximum number of URLs that can be uploaded to the server. Default is 10.
- `MAX_CACHE_DIR_SIZE` - Sets the maximum size of the cache directory. Default is 1073741824 bytes (1GB). Once limit is reached, the oldest files will be deleted.
- `RATE_LIMIT_ENABLED` - Enables per-client REST rate limiting for `/compile` endpoints. Default is `true`.
- `RATE_LIMIT_REQUESTS_PER_MINUTE` - Sets the maximum number of `/compile` requests per client per minute. Default is 120.
- `PROXY_ADDRESS_FORWARDING` - Trusts `X-Forwarded-*` request information when set to `true`. Default is `false`.
- `TRUSTED_PROXIES` - Comma-separated proxy IP addresses or CIDR ranges allowed to provide forwarded request information. Default is `127.0.0.1`.

All of the above can be set in the `application.properties` file or provided as environment variables to the docker container.

Server defaults are defined in `src/main/resources/application.properties`. The container image uses these defaults, except for `CACHE_DIR=/volume`.

The HTTP server (`quarkus.http.limits.max-body-size`) and any reverse proxy may impose a lower body limit. Configure those separately when increasing the application limits; responses rejected by those layers may use their own error format.

When running behind a reverse proxy, set `PROXY_ADDRESS_FORWARDING=true` and configure `TRUSTED_PROXIES`
with the proxy's address or network. The proxy must replace client-provided `X-Forwarded-*` headers rather
than append to untrusted values. Forwarded headers remain ignored when proxy forwarding is disabled or the
direct peer is not trusted.

### OpenAPI

The application provides an OpenAPI specification at `openapi.yaml`

### License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

### Acknowledgements

- [Quarkus](https://quarkus.io/)
- [Google Closure Compiler](https://developers.google.com/closure/compiler)
- [Alpine.js](https://alpinejs.dev/)
- [Tailwind CSS](https://tailwindcss.com/)

### Donate

If you like this project, consider donating to the developer:

- [Patreon](https://www.patreon.com/c/high_on_toes/membership)
- [BuyMeACoffee](https://www.buymeacoffee.com/{placeholder})

### Bugs and Feature Requests

Please use the Github Issues page to report any bugs or request new features.
