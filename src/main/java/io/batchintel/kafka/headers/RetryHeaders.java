package io.batchintel.kafka.headers;

import io.batchintel.utils.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

public final class RetryHeaders {

    private RetryHeaders() {}

    /**
     * Reads the x-retry-attempt header from a consumer record.
     * Returns 0 if the header is absent (i.e. first delivery, not a retry).
     */
    public static int getAttemptCount(ConsumerRecord<?, ?> record) {
        var header = record.headers().lastHeader(Constants.HEADER_RETRY_ATTEMPT);
        if (header == null) {
            return 0;
        }
        try {
            return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Writes (or replaces) the x-retry-attempt header in a Headers instance.
     * Used by the DLQ recoverer to stamp the final attempt count on the dead-letter record.
     */
    public static void setAttemptCount(Headers headers, int count) {
        headers.remove(Constants.HEADER_RETRY_ATTEMPT);
        headers.add(Constants.HEADER_RETRY_ATTEMPT,
                String.valueOf(count).getBytes(StandardCharsets.UTF_8));
    }
}
