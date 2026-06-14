package io.batchintel.domain.incidents;

import io.batchintel.domain.metrics.JobType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record IncidentFingerprint(String value) {

    public static IncidentFingerprint of(JobType jobType,
                                         Incident.Severity severity,
                                         Instant detectedAt) {
        String raw = jobType.name() + "|" + severity.name() + "|" + detectedAt.truncatedTo(ChronoUnit.HOURS);
        return new IncidentFingerprint(sha256Hex(raw));
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
