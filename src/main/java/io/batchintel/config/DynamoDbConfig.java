package io.batchintel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${aws.dynamodb.endpoint:}") String endpoint,
            @Value("${aws.dynamodb.region}") String region,
            @Value("${aws.credentials.access-key-id:}") String accessKeyId,
            @Value("${aws.credentials.secret-access-key:}") String secretAccessKey) {

        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(region));

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (!accessKeyId.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        }
        // else: SDK default credential chain resolves IAM instance profile on EC2

        return builder.build();
    }
}
