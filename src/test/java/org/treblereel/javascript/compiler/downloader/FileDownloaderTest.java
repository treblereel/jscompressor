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

import org.junit.jupiter.api.Test;
import org.treblereel.javascript.compiler.config.ServerConfig;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileDownloaderTest {

  @Test
  public void rejectsUnsupportedUrlSchemes() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("file:///etc/passwd"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("ftp://example.com/test.js"));
  }

  @Test
  public void rejectsLoopbackAndPrivateAddresses() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://localhost/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://127.0.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://[::1]/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://10.0.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://172.16.0.1/test.js"));
    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("http://192.168.0.1/test.js"));
  }

  @Test
  public void rejectsCloudMetadataAddress() {
    FileDownloader downloader = createDownloader();

    assertThrows(
        IOException.class,
        () -> downloader.downloadFileToTemp("http://169.254.169.254/latest/meta-data/"));
  }

  @Test
  public void rejectsUrlUserInfo() {
    FileDownloader downloader = createDownloader();

    assertThrows(IOException.class, () -> downloader.downloadFileToTemp("https://user@example.com/test.js"));
  }

  private FileDownloader createDownloader() {
    FileDownloader downloader = new FileDownloader();
    downloader.serverConfig = new TestServerConfig();
    return downloader;
  }

  private static class TestServerConfig implements ServerConfig {

    @Override
    public long downloadFileMaxSize() {
      return 1024;
    }

    @Override
    public long requestBodyMaxSize() {
      return 0;
    }

    @Override
    public long cacheMaxSize() {
      return 1024;
    }

    @Override
    public long downloadUrlsPreRequest() {
      return 10;
    }

    @Override
    public int downloadConnectTimeoutMs() {
      return 5000;
    }

    @Override
    public int downloadReadTimeoutMs() {
      return 10000;
    }

    @Override
    public int downloadMaxRedirects() {
      return 5;
    }

    @Override
    public String cacheLocation() {
      return "cache";
    }

    @Override
    public boolean rateLimitEnabled() {
      return true;
    }

    @Override
    public long rateLimitRequestsPerMinute() {
      return 120;
    }
  }
}
