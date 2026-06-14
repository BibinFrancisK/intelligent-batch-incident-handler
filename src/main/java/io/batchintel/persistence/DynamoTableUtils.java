package io.batchintel.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

public class DynamoTableUtils {

    private static final Logger log = LoggerFactory.getLogger(DynamoTableUtils.class);

    private DynamoTableUtils() {}

    public static void createIfAbsent(DynamoDbClient ddb, String table, String pk) {
        if (exists(ddb, table)) {
            log.info("Table {} already present", table);
            return;
        }
        ddb.createTable(b -> b
                .tableName(table)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(KeySchemaElement.builder()
                        .attributeName(pk)
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(pk)
                        .attributeType(ScalarAttributeType.S)
                        .build()));
        ddb.waiter().waitUntilTableExists(b -> b.tableName(table));
        log.info("Created table {}", table);
    }

    public static boolean exists(DynamoDbClient ddb, String table) {
        try {
            ddb.describeTable(b -> b.tableName(table));
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }
}
