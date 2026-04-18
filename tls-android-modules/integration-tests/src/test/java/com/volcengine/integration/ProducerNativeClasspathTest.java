package com.volcengine.integration;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerNativeClasspathTest {

  @Test
  void producerNativeClassesAreResolvableFromArtifact() throws Exception {
    try (URLClassLoader loader = ProducerNativeApiContractTest.producerNativeClassLoader()) {
      Class<?> logClass = loader.loadClass("com.volcengine.tls.android.producer.Log");
      Class<?> clientClass = loader.loadClass("com.volcengine.tls.android.producer.LogProducerClient");
      Class<?> resultClass = loader.loadClass("com.volcengine.tls.android.producer.LogProducerResult");
      Class<?> bridgeClass = loader.loadClass("com.volcengine.tls.android.producer.internal.JniNativeProducerBridge");

      assertNotNull(logClass.getProtectionDomain().getCodeSource());
      assertNotNull(clientClass.getProtectionDomain().getCodeSource());
      assertNotNull(resultClass.getProtectionDomain().getCodeSource());
      assertNotNull(bridgeClass.getProtectionDomain().getCodeSource());

      String source = String.valueOf(clientClass.getProtectionDomain().getCodeSource().getLocation());
      assertTrue(source.contains("producer-native") || source.contains("classes.jar"));
    }
  }
}
