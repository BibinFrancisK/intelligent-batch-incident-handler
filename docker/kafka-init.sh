#!/bin/bash
set -e

KAFKA="kafka-topics --bootstrap-server kafka:9092"

$KAFKA --create --if-not-exists \
  --topic batch.events.v1 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

$KAFKA --create --if-not-exists \
  --topic batch.events.v1.retry \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=259200000

$KAFKA --create --if-not-exists \
  --topic batch.events.v1.dlq \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=1209600000

$KAFKA --create --if-not-exists \
  --topic incidents.v1 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=2592000000

echo "=== Topics ready ==="
$KAFKA --list
