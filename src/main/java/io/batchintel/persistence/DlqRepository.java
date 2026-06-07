package io.batchintel.persistence;

import io.batchintel.utils.Constants;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;

@Component
public class DlqRepository {

    private final DynamoDbClient ddb;

    public DlqRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    public void save(String eventId, String rawPayload, String error, int retryCount) {
        ddb.putItem(b -> b
                .tableName(Constants.TABLE_DLQ_EVENTS)
                .item(Map.of(
                        Constants.ATTR_DLQ_EVENT_ID, AttributeValue.fromS(eventId),
                        Constants.ATTR_DLQ_RAW_PAYLOAD, AttributeValue.fromS(rawPayload != null ? rawPayload : ""),
                        Constants.ATTR_DLQ_ERROR, AttributeValue.fromS(error != null ? error : "unknown"),
                        Constants.ATTR_DLQ_RETRY_COUNT, AttributeValue.fromN(String.valueOf(retryCount)),
                        Constants.ATTR_DLQ_FAILED_AT, AttributeValue.fromS(Instant.now().toString())
                )));
    }
}
