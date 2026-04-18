package com.volcengine.tls.android.producer.internal;

import com.volcengine.tls.android.producer.LogProducerResult;

final class ResultMapper {
    private static final int RESULT_OK = 0;
    private static final int RESULT_INVALID = 1;
    private static final int RESULT_DROP_ERROR = 2;
    private static final int RESULT_PERSISTENT_ERROR = 3;
    private static final int RESULT_CLOSED = 4;
    private static final int RESULT_TIMEOUT = 5;

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private ResultMapper() {
    }

    static LogProducerResult map(
            int nativeResult,
            int httpCode,
            String requestId,
            String errorCode,
            String errorMessage,
            int transportKind,
            int transportCode,
            long logBytes,
            long compressedBytes) {
        return new LogProducerResult(
                mapCode(nativeResult, httpCode, transportKind, transportCode),
                requestId,
                errorCode,
                errorMessage,
                httpCode,
                transportKind,
                transportCode,
                logBytes,
                compressedBytes);
    }

    private static LogProducerResult.Code mapCode(
            int nativeResult,
            int httpCode,
            int transportKind,
            int transportCode) {
        switch (nativeResult) {
            case RESULT_OK:
                return LogProducerResult.Code.OK;
            case RESULT_INVALID:
                return LogProducerResult.Code.INVALID;
            case RESULT_PERSISTENT_ERROR:
                return LogProducerResult.Code.PERSISTENT_ERROR;
            case RESULT_CLOSED:
                return LogProducerResult.Code.CLOSED;
            case RESULT_TIMEOUT:
                return LogProducerResult.Code.TIMEOUT;
            case RESULT_DROP_ERROR:
                return mapDropError(httpCode, transportKind, transportCode);
            default:
                return LogProducerResult.Code.UNKNOWN_ERROR;
        }
    }

    private static LogProducerResult.Code mapDropError(int httpCode, int transportKind, int transportCode) {
        if (httpCode == HTTP_UNAUTHORIZED || httpCode == HTTP_FORBIDDEN) {
            return LogProducerResult.Code.AUTH_ERROR;
        }
        if (httpCode <= 0 || transportKind != 0 || transportCode != 0) {
            return LogProducerResult.Code.NETWORK_ERROR;
        }
        if (httpCode >= 400) {
            return LogProducerResult.Code.SERVER_ERROR;
        }
        return LogProducerResult.Code.DROP_ERROR;
    }
}
