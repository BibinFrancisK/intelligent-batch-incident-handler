package io.batchintel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IncidentIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentIntelligenceApplication.class, args);
    }
}
