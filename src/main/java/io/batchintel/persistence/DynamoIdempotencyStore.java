package io.batchintel.persistence;

import io.batchintel.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "incident.persistence.idempotency", havingValue = "dynamo", matchIfMissing = true)
public class DynamoIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(DynamoIdempotencyStore.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final DynamoDbClient ddb;

    public DynamoIdempotencyStore(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    @Override
    public boolean isNew(String eventId) {
        long ttlEpoch = Instant.now().plus(TTL).getEpochSecond();
        try {
            ddb.putItem(b -> b
                    .tableName(Constants.TABLE_PROCESSED_EVENTS)
                    .item(Map.of(
                            Constants.ATTR_EVENT_ID, AttributeValue.fromS(eventId),
                            Constants.ATTR_TTL,      AttributeValue.fromN(Long.toString(ttlEpoch))))
                    .conditionExpression("attribute_not_exists(" + Constants.ATTR_EVENT_ID + ")"));
            return true; // conditional write succeeded — first time this eventId was seen
        } catch (ConditionalCheckFailedException e) {
            log.debug("Idempotency hit for eventId={}", eventId);
            return false; // row already exists — duplicate, skip processing
        }
    }
}
