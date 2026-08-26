package com.volcengine.tls.android.producer;

import junit.framework.TestCase;

public final class ProducerNativeLoadInstrumentedTest extends TestCase {

    public void testNativeCreateAndDestroyWithoutSending() {
        LogProducerConfig config = new LogProducerConfig()
                .setEndpoint("https://tls-cn-beijing.volces.com")
                .setRegion("cn-beijing")
                .setTopicId("native-load-smoke")
                .setAccessKeyId("test-ak")
                .setAccessKeySecret("test-sk")
                .setDestroyFlusherWaitMs(1000)
                .setDestroySenderWaitMs(1000);

        LogProducerClient client = new LogProducerClient(config, null);
        client.destroyLogProducer();
        assertTrue("native producer destroy timed out", client.awaitDestroy(5000));
    }
}
