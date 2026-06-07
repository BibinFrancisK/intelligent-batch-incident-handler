package io.batchintel.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker llmProviderCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("llm-provider");
    }

    @Bean
    public CircuitBreaker slackNotifierCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("slack-notifier");
    }
}
