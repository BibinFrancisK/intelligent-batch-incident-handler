package io.batchintel.notify;

import io.batchintel.domain.incidents.Incident;

public sealed interface Notifier permits NoopNotifier, SlackNotifier {
    void notify(Incident incident);

    String name();
}
