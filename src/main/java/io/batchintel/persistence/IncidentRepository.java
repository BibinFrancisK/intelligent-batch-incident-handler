package io.batchintel.persistence;

import io.batchintel.domain.incidents.Incident;
import io.batchintel.utils.Constants;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

@Component
public class IncidentRepository {

    private final DynamoDbClient ddb;

    public IncidentRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    public void save(Incident incident) {
        ddb.putItem(b -> b
                .tableName(Constants.TABLE_INCIDENTS)
                .item(toAttributeMap(incident)));
    }

    private Map<String, AttributeValue> toAttributeMap(Incident i) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(Constants.ATTR_INCIDENT_ID,       AttributeValue.fromS(i.incidentId()));
        item.put(Constants.ATTR_SCHEMA_VERSION,    AttributeValue.fromS(i.schemaVersion()));
        item.put(Constants.ATTR_JOB_TYPE,          AttributeValue.fromS(i.jobType().name()));
        item.put(Constants.ATTR_ANOMALY_SCORE,     AttributeValue.fromN(String.valueOf(i.anomalyScore())));
        item.put(Constants.ATTR_DETECTOR_NAME,     AttributeValue.fromS(i.detectorName()));
        item.put(Constants.ATTR_FINGERPRINT,       AttributeValue.fromS(i.fingerprint()));
        item.put(Constants.ATTR_SEVERITY,          AttributeValue.fromS(i.severity().name()));
        item.put(Constants.ATTR_LLM_PROVIDER,      AttributeValue.fromS(i.llmProvider()));
        item.put(Constants.ATTR_LLM_UNAVAILABLE,   AttributeValue.fromBool(i.llmUnavailable()));
        item.put(Constants.ATTR_DETECTED_AT,       AttributeValue.fromS(i.detectedAt().toString()));
        if (i.summary() != null) {
            item.put(Constants.ATTR_SUMMARY, AttributeValue.fromS(i.summary()));
        }
        if (i.likelyCause() != null) {
            item.put(Constants.ATTR_LIKELY_CAUSE, AttributeValue.fromS(i.likelyCause()));
        }
        if (!i.suggestedActions().isEmpty()) {
            item.put(Constants.ATTR_SUGGESTED_ACTIONS,
                    AttributeValue.fromL(i.suggestedActions().stream()
                            .map(AttributeValue::fromS)
                            .toList()));
        }
        return item;
    }
}
