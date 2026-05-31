package io.batchintel.persistence;

public interface IdempotencyStore {

    /**
     * Records the eventId as seen and returns true if this is the first time it was seen.
     * Returns false (and is a no-op) if the eventId was already recorded.
     */
    boolean isNew(String eventId);
}
