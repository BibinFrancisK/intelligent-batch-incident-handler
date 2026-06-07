package io.batchintel.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.batchintel.kafka.headers.RetryHeaders;
import io.batchintel.utils.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
public class KafkaErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    // DefaultErrorHandler decides whether a message needs to be retried or sent to the DLQ.
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {

        /* Stores the attempt number for a given message.
         * Both RetryListener and setHeadersFunction needs this, and they run on the same thread.
         */
        ThreadLocal<Integer> lastAttempt = new ThreadLocal<>();

        /* Send message to DLQ after all retries failed.
         * -1 ==> let Kafka pick the partition.
         */
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(Constants.TOPIC_BATCH_EVENTS_DLQ, -1));

        // Append x-retry-attempt to the DLQ record's headers
        recoverer.setHeadersFunction((record, ex) -> {
            var extra = new RecordHeaders();
            RetryHeaders.setAttemptCount(extra, lastAttempt.get() != null ? lastAttempt.get() : 1);
            lastAttempt.remove(); // clean up — do not let the count bleed into the next message
            return extra;
        });

        // 3 retries → delays of 1s, 5s, 25s → then DLQ
        var backoff = new ExponentialBackOffWithMaxRetries(3);
        backoff.setInitialInterval(1_000L);
        backoff.setMultiplier(5.0);
        backoff.setMaxInterval(25_000L);

        // wire recoverer and backoff together
        var errorHandler = new DefaultErrorHandler(recoverer, backoff);

        // No point in retrying JSON parse errors, send it to DLQ right away.
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);

        errorHandler.setRetryListeners(new RetryListener() {

            //When a message failed delivery
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                lastAttempt.set(deliveryAttempt);
                log.warn("Delivery failed attempt={} topic={} partition={} offset={}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset());
            }

            //When a message is sent to DLQ after all retries failed.
            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.warn("Record routed to DLQ topic={} partition={} offset={} reason={}",
                        record.topic(), record.partition(), record.offset(),
                        ex.getClass().getSimpleName());
            }

            // When a message failed to be sent to DLQ.
            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                log.error("DLQ recovery itself failed topic={} partition={} offset={} cause={}",
                        record.topic(), record.partition(), record.offset(), failure.getMessage());
            }
        });

        return errorHandler;
    }
}
