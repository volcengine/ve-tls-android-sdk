package com.volcengine.integration;

import com.volcengine.model.tls.util.AdaptorUtil;
import com.volcengine.service.tls.TLSLogClientImpl;
import org.junit.jupiter.api.Test;

public class ClasspathDiagnosticsTest {
  @Test
  void print_class_sources() {
    System.out.println("AdaptorUtil loaded from: " + AdaptorUtil.class.getProtectionDomain().getCodeSource());
    System.out.println("TLSLogClientImpl loaded from: " + TLSLogClientImpl.class.getProtectionDomain().getCodeSource());
  }
}

