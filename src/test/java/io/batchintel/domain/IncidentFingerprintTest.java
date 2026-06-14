package io.batchintel.domain;

import io.batchintel.domain.incidents.Incident;
import io.batchintel.domain.incidents.IncidentFingerprint;
import io.batchintel.domain.metrics.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentFingerprintTest {

    private static final JobType JOB = JobType.ANNUITY_PAYOUT;
    private static final Incident.Severity SEV = Incident.Severity.HIGH;
    private static final Instant BASE = Instant.parse("2024-06-01T14:32:00Z");

    @Test
    @DisplayName("same inputs produce identical fingerprint")
    void deterministicForSameInputs() {
        IncidentFingerprint a = IncidentFingerprint.of(JOB, SEV, BASE);
        IncidentFingerprint b = IncidentFingerprint.of(JOB, SEV, BASE);

        assertThat(a.value()).isEqualTo(b.value());
    }

    @Test
    @DisplayName("timestamps within the same hour produce the same fingerprint")
    void sameHourBucketYieldsSameFingerprint() {
        Instant startOfHour = BASE.truncatedTo(ChronoUnit.HOURS);
        Instant endOfHour   = startOfHour.plusSeconds(3599);

        IncidentFingerprint early = IncidentFingerprint.of(JOB, SEV, startOfHour);
        IncidentFingerprint late  = IncidentFingerprint.of(JOB, SEV, endOfHour);

        assertThat(early.value()).isEqualTo(late.value());
    }

    @Test
    @DisplayName("timestamps in different hours produce different fingerprints")
    void differentHoursYieldDifferentFingerprints() {
        Instant hourOne = BASE.truncatedTo(ChronoUnit.HOURS);
        Instant hourTwo = hourOne.plus(1, ChronoUnit.HOURS);

        IncidentFingerprint fp1 = IncidentFingerprint.of(JOB, SEV, hourOne);
        IncidentFingerprint fp2 = IncidentFingerprint.of(JOB, SEV, hourTwo);

        assertThat(fp1.value()).isNotEqualTo(fp2.value());
    }

    @Test
    @DisplayName("different severity produces different fingerprint for same job and hour")
    void differentSeverityYieldsDifferentFingerprint() {
        IncidentFingerprint high   = IncidentFingerprint.of(JOB, Incident.Severity.HIGH,   BASE);
        IncidentFingerprint medium = IncidentFingerprint.of(JOB, Incident.Severity.MEDIUM, BASE);
        IncidentFingerprint low    = IncidentFingerprint.of(JOB, Incident.Severity.LOW,    BASE);

        assertThat(high.value()).isNotEqualTo(medium.value());
        assertThat(medium.value()).isNotEqualTo(low.value());
        assertThat(high.value()).isNotEqualTo(low.value());
    }

    @Test
    @DisplayName("different job type produces different fingerprint for same severity and hour")
    void differentJobTypeYieldsDifferentFingerprint() {
        JobType otherJob = JobType.values()[0] == JOB ? JobType.values()[1] : JobType.values()[0];

        IncidentFingerprint fp1 = IncidentFingerprint.of(JOB,      SEV, BASE);
        IncidentFingerprint fp2 = IncidentFingerprint.of(otherJob, SEV, BASE);

        assertThat(fp1.value()).isNotEqualTo(fp2.value());
    }

    @Test
    @DisplayName("fingerprint value is a 64-character lowercase hex SHA-256 string")
    void fingerprintIsValidSha256Hex() {
        IncidentFingerprint fp = IncidentFingerprint.of(JOB, SEV, BASE);

        assertThat(fp.value())
                .hasSize(64)
                .matches("[0-9a-f]+");
    }
}
