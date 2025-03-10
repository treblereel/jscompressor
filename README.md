## JSCompressor
A web application serving as both a user-friendly web interface and a REST API for Google's Closure Compiler.

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

#### Build Native Image
1. Clone the repository
2. Run `mvn clean package -Pnative`
3. Run `./target/jscompressor-0.1-runner`

#### Docker/Podman

# JVM and Native images are available in the Docker Hub
`https://hub.docker.com/repository/docker/treblereel/jscompressor/general`
`https://hub.docker.com/repository/docker/treblereel/jscompressor-native/general`

## JVM based
1. Clone the repository
2. Run `mvn clean package`
3. Run `docker build -f src/main/docker/Dockerfile.jvm -t quarkus/jscompressor .`
4. Run `docker run -d -v /home/${username}/${volumes}:/volume:Z -p 8080:8080 localhost/quarkus/jscompressor:latest`

## Native based
1. Clone the repository
2. Run `mvn clean package -Pnative`
3. Run `docker build -f src/main/docker/Dockerfile.native -t quarkus/jscompressor .`
4. Run `docker run -d -v /home/${username}/${volumes}:/volume:Z -p 8080:8080 localhost/quarkus/jscompressor:latest`

Note: You should be familiar with such topics like Docker root/rootless containers, selinux and such topics. For
instance, if you get `permission denied` error, you should check the selinux context of the volume. That is why I prefer 
rootless Podman containers, that configure the context automatically.

Tips: If you are a mac user, check you build the native image for Linux. (docs docker buildx)

Tips: https://quarkus.io/guides/container-image

### Configuration

The application can be configured using the following environment variables:

- `MAX_DOWNLOAD_FILE_SIZE` - Sets the maximum file/script size that can be uploaded to the server. Default is 1048576 bytes (1MB).
- `MAX_DOWNLOAD_URLS_PER_REQUEST` - Sets the maximum number of URLs that can be uploaded to the server. Default is 10.
- `MAX_CACHE_DIR_SIZE` - Sets the maximum size of the cache directory. Default is 1073741824 bytes (1GB). Once limit is reached, the oldest files will be deleted.

All of the above can be set in the `application.properties` file or provided as environment variables to the docker container.

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
