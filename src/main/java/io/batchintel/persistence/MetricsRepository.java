package io.batchintel.persistence;

import io.batchintel.domain.metrics.JobType;
import io.batchintel.domain.metrics.RollingMetrics;
import io.batchintel.utils.Constants;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class MetricsRepository {

    private final DynamoDbClient ddb;

    public MetricsRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    public void accumulate(JobType jobType, double durationSeconds, int errorCount, long rows) {
        ddb.updateItem(b -> b
                .tableName(Constants.TABLE_METRICS_STATE)
                .key(Map.of(Constants.ATTR_JOB_TYPE, AttributeValue.fromS(jobType.name())))
                .updateExpression("ADD #count :one, #dur :dur, #err :err, #rows :rows SET #upd = :now")
                .expressionAttributeNames(Map.of(
                        "#count", Constants.ATTR_COUNT,
                        "#dur",   Constants.ATTR_SUM_DURATION_SECONDS,
                        "#err",   Constants.ATTR_SUM_ERROR_COUNT,
                        "#rows",  Constants.ATTR_SUM_ROWS,
                        "#upd",   Constants.ATTR_UPDATED_AT))
                .expressionAttributeValues(Map.of(
                        ":one",  AttributeValue.fromN("1"),
                        ":dur",  AttributeValue.fromN(Double.toString(durationSeconds)),
                        ":err",  AttributeValue.fromN(Integer.toString(errorCount)),
                        ":rows", AttributeValue.fromN(Long.toString(rows)),
                        ":now",  AttributeValue.fromS(Instant.now().toString()))));
    }

    public Optional<RollingMetrics> find(JobType jobType) {
        var response = ddb.getItem(b -> b
                .tableName(Constants.TABLE_METRICS_STATE)
                .key(Map.of(Constants.ATTR_JOB_TYPE, AttributeValue.fromS(jobType.name()))));

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        Map<String, AttributeValue> item = response.item();
        return Optional.of(new RollingMetrics(
                jobType,
                longVal(item, Constants.ATTR_COUNT),
                doubleVal(item, Constants.ATTR_SUM_DURATION_SECONDS),
                longVal(item, Constants.ATTR_SUM_ERROR_COUNT),
                longVal(item, Constants.ATTR_SUM_ROWS),
                Instant.parse(item.get(Constants.ATTR_UPDATED_AT).s())
        ));
    }

    private long longVal(Map<String, AttributeValue> item, String attr) {
        AttributeValue val = item.get(attr);
        return val != null ? Long.parseLong(val.n()) : 0L;
    }

    private double doubleVal(Map<String, AttributeValue> item, String attr) {
        AttributeValue val = item.get(attr);
        return val != null ? Double.parseDouble(val.n()) : 0.0;
    }
}
