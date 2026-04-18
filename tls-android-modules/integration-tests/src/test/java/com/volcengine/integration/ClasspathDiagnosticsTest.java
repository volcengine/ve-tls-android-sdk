package com.volcengine.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClasspathDiagnosticsTest {
  @Test
  void producerLiteClasses_areAbsent_afterCutover() throws Exception {
    assertNotNull(Class.forName("com.volcengine.tls.android.producer.LogProducerClient"));
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.volcengine.service.tls.ProducerImpl"));
  }
}
