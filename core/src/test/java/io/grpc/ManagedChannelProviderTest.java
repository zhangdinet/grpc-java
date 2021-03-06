/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ServiceConfigurationError;

/** Unit tests for {@link ManagedChannelProvider}. */
@RunWith(JUnit4.class)
public class ManagedChannelProviderTest {
  private final String serviceFile = "META-INF/services/io.grpc.ManagedChannelProvider";

  @Test(expected = ManagedChannelProvider.ProviderNotFoundException.class)
  public void noProvider() {
    ManagedChannelProvider.provider();
  }

  @Test
  public void multipleProvider() {
    ClassLoader cl = new ReplacingClassLoader(getClass().getClassLoader(), serviceFile,
        "io/grpc/ManagedChannelProviderTest-multipleProvider.txt");
    assertSame(Available7Provider.class, ManagedChannelProvider.load(cl).getClass());
  }

  @Test
  public void unavailableProvider() {
    ClassLoader cl = new ReplacingClassLoader(getClass().getClassLoader(), serviceFile,
        "io/grpc/ManagedChannelProviderTest-unavailableProvider.txt");
    assertNull(ManagedChannelProvider.load(cl));
  }

  @Test
  public void getCandidatesViaHardCoded_usesProvidedClassLoader() {
    final RuntimeException toThrow = new RuntimeException();
    try {
      ManagedChannelProvider.getCandidatesViaHardCoded(new ClassLoader() {
        @Override
        public Class<?> loadClass(String name) {
          throw toThrow;
        }
      });
      fail("Expected exception");
    } catch (RuntimeException ex) {
      assertSame(toThrow, ex);
    }
  }

  @Test
  public void getCandidatesViaHardCoded_ignoresMissingClasses() {
    Iterable<ManagedChannelProvider> i =
        ManagedChannelProvider.getCandidatesViaHardCoded(new ClassLoader() {
          @Override
          public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException();
          }
        });
    assertFalse("Iterator should be empty", i.iterator().hasNext());
  }

  @Test
  public void create_throwsErrorOnMisconfiguration() throws Exception {
    class PrivateClass {}

    try {
      ManagedChannelProvider.create(PrivateClass.class);
      fail("Expected exception");
    } catch (ServiceConfigurationError e) {
      assertTrue("Expected ClassCastException cause: " + e.getCause(),
          e.getCause() instanceof ClassCastException);
    }
  }

  private static class BaseProvider extends ManagedChannelProvider {
    private final boolean isAvailable;
    private final int priority;

    public BaseProvider(boolean isAvailable, int priority) {
      this.isAvailable = isAvailable;
      this.priority = priority;
    }

    @Override
    protected boolean isAvailable() {
      return isAvailable;
    }

    @Override
    protected int priority() {
      return priority;
    }

    @Override
    protected ManagedChannelBuilder<?> builderForAddress(String host, int port) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected ManagedChannelBuilder<?> builderForTarget(String target) {
      throw new UnsupportedOperationException();
    }
  }

  public static class Available0Provider extends BaseProvider {
    public Available0Provider() {
      super(true, 0);
    }
  }

  public static class Available5Provider extends BaseProvider {
    public Available5Provider() {
      super(true, 5);
    }
  }

  public static class Available7Provider extends BaseProvider {
    public Available7Provider() {
      super(true, 7);
    }
  }

  public static class UnavailableProvider extends BaseProvider {
    public UnavailableProvider() {
      super(false, 10);
    }

    @Override
    protected int priority() {
      throw new RuntimeException("purposefully broken");
    }
  }
}
