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

    @Test
    public void result_exposesFailureConvenienceViews() {
        LogProducerResult network = new LogProducerResult(
                LogProducerResult.Code.NETWORK_ERROR,
                "rid-net",
                "ConnectTimeout",
                "dial timeout",
                0,
                7,
                110,
                10,
                8
        );
        LogProducerResult server = new LogProducerResult(
                LogProducerResult.Code.SERVER_ERROR,
                "rid-http",
                "AccessDenied",
                null,
                403,
                0,
                0,
                10,
                8
        );
        LogProducerResult invalid = new LogProducerResult(
                LogProducerResult.Code.INVALID,
                null,
                null,
                "bad config",
                0,
                0,
                0,
                0,
                0
        );

        assertEquals("dial timeout", network.getBestErrorMessage());
        assertEquals(true, network.hasTransportFailure());
        assertEquals(false, network.hasHttpFailure());
        assertEquals(LogProducerResult.FailureKind.TRANSPORT, network.getFailureKind());
        assertEquals(
                "kind=TRANSPORT code=NETWORK_ERROR transport=7/110 errorCode=ConnectTimeout message=dial timeout reqId=rid-net",
                network.getFailureSummary()
        );

        assertEquals("AccessDenied", server.getBestErrorMessage());
        assertEquals(false, server.hasTransportFailure());
        assertEquals(true, server.hasHttpFailure());
        assertEquals(LogProducerResult.FailureKind.HTTP, server.getFailureKind());
        assertEquals(
                "kind=HTTP code=SERVER_ERROR http=403 errorCode=AccessDenied reqId=rid-http",
                server.getFailureSummary()
        );

        assertEquals(LogProducerResult.FailureKind.VALIDATION, invalid.getFailureKind());
        assertEquals(
                "kind=VALIDATION code=INVALID message=bad config",
                invalid.getFailureSummary()
        );
    }
}
