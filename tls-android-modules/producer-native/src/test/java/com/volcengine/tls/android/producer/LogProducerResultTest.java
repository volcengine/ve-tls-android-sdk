package com.volcengine.tls.android.producer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LogProducerResultTest {

    @Test
    public void result_isImmutableValueObject() {
        LogProducerResult result = new LogProducerResult(
                LogProducerResult.Code.OK,
                "rid",
                null,
                null,
                200,
                0,
                0,
                10,
                8
        );

        assertEquals(LogProducerResult.Code.OK, result.getCode());
        assertEquals("rid", result.getRequestId());
    }
}
