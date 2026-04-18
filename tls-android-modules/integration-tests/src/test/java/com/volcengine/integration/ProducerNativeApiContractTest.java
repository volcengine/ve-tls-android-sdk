package com.volcengine.integration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProducerNativeApiContractTest {

  @Test
  void exposesTlsStyleAddLogAndDestroyPaths() throws Exception {
    try (URLClassLoader loader = producerNativeClassLoader()) {
      Class<?> clientClass = loader.loadClass("com.volcengine.tls.android.producer.LogProducerClient");
      Class<?> logClass = loader.loadClass("com.volcengine.tls.android.producer.Log");
      Class<?> configClass = loader.loadClass("com.volcengine.tls.android.producer.LogProducerConfig");

      Method addLog = clientClass.getMethod("addLog", logClass);
      assertEquals(void.class, addLog.getReturnType());

      Method destroy = clientClass.getMethod("destroyLogProducer");
      assertEquals(void.class, destroy.getReturnType());

      assertNotNull(clientClass.getConstructor(configClass));
    }
  }

  @Test
  void jniBridgeAddLogPathIsNoLongerStubbed() throws Exception {
    try (URLClassLoader loader = producerNativeClassLoader()) {
      Class<?> bridgeClass = loader.loadClass("com.volcengine.tls.android.producer.internal.JniNativeProducerBridge");
      Class<?> logClass = loader.loadClass("com.volcengine.tls.android.producer.Log");
      Object bridge = bridgeClass.getConstructor().newInstance();
      Object log = logClass.getConstructor().newInstance();
      Method putContent = logClass.getMethod("putContent", String.class, String.class);
      putContent.invoke(log, "k", "v");

      Method addLog = bridgeClass.getMethod("addLog", long.class, logClass, int.class);
      InvocationTargetException error = assertThrows(
          InvocationTargetException.class,
          () -> addLog.invoke(bridge, 1L, log, 0));

      assertNotNull(error.getCause());
      assertNotSame(UnsupportedOperationException.class, error.getCause().getClass());
    }
  }

  static URLClassLoader producerNativeClassLoader() throws Exception {
    Path jarPath = findProducerNativeJar();
    return new ChildFirstUrlClassLoader(new URL[]{jarPath.toUri().toURL()}, ProducerNativeApiContractTest.class.getClassLoader());
  }

  private static Path findProducerNativeJar() {
    Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    Path direct = cwd.resolve("producer-native/build/intermediates/aar_main_jar/release/syncReleaseLibJars/classes.jar");
    if (Files.exists(direct)) {
      return direct;
    }
    Path sibling = cwd.resolve("../producer-native/build/intermediates/aar_main_jar/release/syncReleaseLibJars/classes.jar").normalize();
    if (Files.exists(sibling)) {
      return sibling;
    }
    throw new IllegalStateException("producer-native classes.jar not found from " + cwd);
  }

  private static final class ChildFirstUrlClassLoader extends URLClassLoader {
    private static final String OVERRIDE_PREFIX = "com.volcengine.tls.android.producer";

    ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.startsWith(OVERRIDE_PREFIX)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          try {
            loaded = findClass(name);
          } catch (ClassNotFoundException ignored) {
            loaded = super.loadClass(name, false);
          }
        }
        if (resolve) {
          resolveClass(loaded);
        }
        return loaded;
      }
      return super.loadClass(name, resolve);
    }
  }
}
