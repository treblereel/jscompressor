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

package org.treblereel.javascript.compiler.rest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RequestSizeFilterTest {

  @Test
  public void acceptsEmptyAndExactLimitBodies() throws Exception {
    assertArrayEquals(new byte[0],
        RequestSizeFilter.readEntity(new ByteArrayInputStream(new byte[0]), 16));
    byte[] content = new byte[16384];
    Arrays.fill(content, (byte) 1);
    assertArrayEquals(content,
        RequestSizeFilter.readEntity(new ByteArrayInputStream(content), content.length));
  }

  @Test
  public void stopsReadingAtFirstByteOverLimit() throws Exception {
    CountingInputStream input = new CountingInputStream();
    assertNull(RequestSizeFilter.readEntity(input, 16384));
    assertEquals(16385, input.bytesRead);
  }

  @Test
  public void derivesBodyLimitFromPayloadUnlessOverridden() {
    assertEquals(6 * 1024 + 65536, RequestSizeFilter.bodyLimit(0, 1024));
    assertEquals(512, RequestSizeFilter.bodyLimit(512, 1024));
  }

  private static class CountingInputStream extends InputStream {
    private long bytesRead;

    @Override
    public int read() {
      bytesRead++;
      return 1;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      Arrays.fill(buffer, offset, offset + length, (byte) 1);
      bytesRead += length;
      return length;
    }
  }
}
