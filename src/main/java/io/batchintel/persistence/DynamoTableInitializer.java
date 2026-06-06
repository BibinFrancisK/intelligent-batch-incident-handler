package io.batchintel.persistence;

import io.batchintel.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;

@Component
@Profile("local")
public class DynamoTableInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DynamoTableInitializer.class);

    private final DynamoDbClient ddb;

    public DynamoTableInitializer(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    @Override
    public void run(ApplicationArguments args) {
        DynamoTableUtils.createIfAbsent(ddb, Constants.TABLE_PROCESSED_EVENTS, Constants.ATTR_EVENT_ID);
        DynamoTableUtils.createIfAbsent(ddb, Constants.TABLE_METRICS_STATE,    Constants.ATTR_JOB_TYPE);
        DynamoTableUtils.createIfAbsent(ddb, Constants.TABLE_INCIDENTS,        Constants.ATTR_INCIDENT_ID);
        enableTtl(Constants.TABLE_PROCESSED_EVENTS, Constants.ATTR_TTL);
    }

    private void enableTtl(String table, String ttlAttribute) {
        try {
            ddb.updateTimeToLive(b -> b
                    .tableName(table)
                    .timeToLiveSpecification(TimeToLiveSpecification.builder()
                            .attributeName(ttlAttribute)
                            .enabled(true)
                            .build()));
            log.info("TTL enabled on {} - {}", table, ttlAttribute);
        } catch (Exception e) {
            // DynamoDB Local accepts the call; real AWS throws if TTL is already enabled
            log.debug("TTL update for {}: {}", table, e.getMessage());
        }
    }
}
