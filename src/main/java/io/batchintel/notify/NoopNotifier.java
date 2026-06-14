package io.batchintel.notify;

import io.batchintel.domain.incidents.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "incident.notify.provider", havingValue = "noop")
public final class NoopNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(NoopNotifier.class);

    @Override
    public void notify(Incident incident) {
        log.info("NoopNotifier: incidentId={} severity={} (Slack disabled)",
                incident.incidentId(), incident.severity());
    }

    @Override
    public String name() {
        return "noop";
    }
}
