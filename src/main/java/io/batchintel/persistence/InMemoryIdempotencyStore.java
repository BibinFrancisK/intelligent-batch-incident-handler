package io.batchintel.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "incident.persistence.idempotency", havingValue = "memory")
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIdempotencyStore.class);

    //replaced by DDB impl in production
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isNew(String eventId) {
        boolean added = seen.add(eventId);
        if (!added) {
            log.debug("Idempotency hit for eventId={}", eventId);
        }
        return added;
    }
}
