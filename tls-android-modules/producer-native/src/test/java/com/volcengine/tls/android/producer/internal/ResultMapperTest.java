package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.LogProducerResult;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ResultMapperTest {

    @Test
    public void dropErrorWith403MapsToAuthError() {
        LogProducerResult result = mapDropError(403, 0, 0, "rid-403");

        assertEquals(LogProducerResult.Code.AUTH_ERROR, result.getCode());
        assertEquals("rid-403", result.getRequestId());
        assertEquals("AccessDenied", result.getErrorCode());
        assertEquals("forbidden", result.getErrorMessage());
        assertEquals(403, result.getHttpCode());
    }

    @Test
    public void dropErrorWith4xxHttpFailureMapsToServerError() {
        assertEquals(LogProducerResult.Code.SERVER_ERROR, mapDropError(400, 0, 0, "rid-400").getCode());
        assertEquals(LogProducerResult.Code.SERVER_ERROR, mapDropError(429, 0, 0, "rid-429").getCode());
    }

    @Test
    public void dropErrorWithoutHttpStatusMapsToNetworkError() {
        LogProducerResult result = mapDropError(0, 0, 0, "rid-no-http");

        assertEquals(LogProducerResult.Code.NETWORK_ERROR, result.getCode());
        assertEquals(0, result.getHttpCode());
    }

    @Test
    public void dropErrorWithTransportFailureMapsToNetworkError() {
        LogProducerResult result = mapDropError(0, 7, 10061, "rid-transport");

        assertEquals(LogProducerResult.Code.NETWORK_ERROR, result.getCode());
        assertEquals(7, result.getTransportKind());
        assertEquals(10061, result.getTransportCode());
    }

    @Test
    public void callbackDispatcherUsesExecutorWhenSenderThreadCallbackDisabled() {
        AtomicInteger postCount = new AtomicInteger();
        AtomicReference<LogProducerResult> delivered = new AtomicReference<>();
        CallbackDispatcher dispatcher = new CallbackDispatcher(
                delivered::set,
                false,
                command -> {
                    postCount.incrementAndGet();
                    command.run();
                });

        dispatcher.dispatch(
                0,
                200,
                "rid-ok",
                null,
                null,
                0,
                0,
                512,
                256
        );

        assertEquals(1, postCount.get());
        assertSame(LogProducerResult.Code.OK, delivered.get().getCode());
        assertEquals("rid-ok", delivered.get().getRequestId());
    }

    private static LogProducerResult mapDropError(
            int httpCode,
            int transportKind,
            int transportCode,
            String requestId) {
        return ResultMapper.map(
                2,
                httpCode,
                requestId,
                "AccessDenied",
                "forbidden",
                transportKind,
                transportCode,
                128,
                64
        );
    }
}
