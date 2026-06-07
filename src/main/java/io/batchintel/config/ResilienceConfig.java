package io.batchintel.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    // CircuitBreakerRegistry is auto-configured by resilience4j-spring-boot3 from the
    // resilience4j.circuitbreaker.instances definitions in application.yml.
    // Micrometer metrics (resilience4j_circuitbreaker_state, _calls_*) are bound
    // automatically when resilience4j-micrometer is on the classpath — no explicit
    // wiring needed.
    //
    // These @Beans expose the named instances as injectable Spring beans so Week 3
    // (LlmProvider, SlackNotifier) can choose between programmatic decoration and
    // the @CircuitBreaker(name="...") AOP annotation — both approaches work.

    @Bean
    public CircuitBreaker llmProviderCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("llm-provider");
    }

    @Bean
    public CircuitBreaker slackNotifierCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("slack-notifier");
    }
}
