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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {
  private final long maxSize;
  private long bytesRead = 0;

  public LimitedInputStream(InputStream in, long maxSize) {
    super(in);
    this.maxSize = maxSize;
  }

  @Override
  public int read() throws IOException {
    checkLimit(1);
    int result = super.read();
    if (result != -1) {
      bytesRead++;
    }
    return result;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    checkLimit(len);
    int result = super.read(b, off, len);
    if (result > 0) {
      bytesRead += result;
    }
    return result;
  }

  private void checkLimit(int bytesToRead) throws IOException {
    if (bytesRead + bytesToRead > maxSize) {
      throw new IOException("File exceeds the maximum allowed size: " + maxSize + " bytes");
    }
  }
}
