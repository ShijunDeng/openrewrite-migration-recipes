package com.huawei.clouds.openrewrite.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Set;
import java.util.regex.Pattern;

/** Marks Kafka 4.1 configuration changes that require operational intent. */
public final class FindKafkaClientPropertiesRisks extends Recipe {
    private static final Set<String> OAUTH_URL_KEYS = Set.of(
            "sasl.oauthbearer.token.endpoint.url", "sasl.oauthbearer.jwks.endpoint.url"
    );
    private static final Pattern REMOVED_METRIC_TOKEN = Pattern.compile(
            "(?:^|[^A-Za-z0-9_-])(?:bufferpool-wait-time-total|io-waittime-total|iotime-total)(?=$|[^A-Za-z0-9_-])"
    );
    private static final Set<String> PRODUCER_KEYS = Set.of(
            "key.serializer", "value.serializer", "acks", "enable.idempotence", "transactional.id",
            "batch.size", "compression.type", "linger.ms", "delivery.timeout.ms", "max.block.ms"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Kafka client 4.1 properties";
    }

    @Override
    public String getDescription() {
        return "Mark OAuth URL allow-listing, idempotent producer in-flight limits, and removed metric names for review.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey();
                String value = e.getValue().getText().trim();
                if (OAUTH_URL_KEYS.contains(key)) {
                    return SearchResult.found(e, "Kafka 4.1 requires this endpoint to be allowed by the org.apache.kafka.sasl.oauthbearer.allowed.urls system property");
                }
                if ("max.in.flight.requests.per.connection".equals(key) && exceedsFive(value) && idempotenceEnabledOrDefault(e)) {
                    return SearchResult.found(e, "With idempotence enabled (including its default), Kafka 4.1 rejects max.in.flight.requests.per.connection greater than 5");
                }
                if (REMOVED_METRIC_TOKEN.matcher(value).find()) {
                    return SearchResult.found(e, "Kafka 4.1 removed a legacy metric name; use its -ns- replacement and recalculate unit-sensitive thresholds");
                }
                if ("bootstrap.servers".equals(key) && producerFile() &&
                    (!hasKey("linger.ms") || !hasKey("enable.idempotence"))) {
                    return SearchResult.found(e, "Producer defaults changed on this migration path: idempotence is enabled and linger.ms is 5; pin intentional values or regression-test latency, batching, retries and ordering");
                }
                if ("group.protocol".equals(key) && "consumer".equalsIgnoreCase(value)) {
                    return SearchResult.found(e, "The consumer group protocol changes heartbeat/session ownership and assignment behavior; roll it out against compatible brokers and test rebalance callbacks and downgrade");
                }
                if ("partition.assignment.strategy".equals(key) && "consumer".equalsIgnoreCase(valueOf("group.protocol"))) {
                    return SearchResult.found(e, "Client-side partition.assignment.strategy does not configure the new consumer group protocol; choose a supported server-side assignor and remove stale assumptions");
                }
                if ("transactional.id".equals(key)) {
                    return SearchResult.found(e, "Kafka 4 strengthens transaction fencing and adds abortable transaction errors; verify stable transactional.id ownership, timeouts, abort paths and rolling deployment");
                }
                return e;
            }

            private boolean idempotenceEnabledOrDefault(Properties.Entry entry) {
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                if (file == null) {
                    return true;
                }
                return file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast)
                        .filter(candidate -> "enable.idempotence".equals(candidate.getKey()))
                        .map(candidate -> candidate.getValue().getText().trim())
                        .noneMatch("false"::equalsIgnoreCase);
            }

            private boolean producerFile() {
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                return file != null && file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast).map(Properties.Entry::getKey)
                        .anyMatch(PRODUCER_KEYS::contains);
            }

            private boolean hasKey(String wanted) {
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                return file != null && file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast).anyMatch(candidate -> wanted.equals(candidate.getKey()));
            }

            private String valueOf(String wanted) {
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                if (file == null) return "";
                return file.getContent().stream().filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast).filter(candidate -> wanted.equals(candidate.getKey()))
                        .map(candidate -> candidate.getValue().getText().trim()).findFirst().orElse("");
            }
        };
    }

    private static boolean exceedsFive(String value) {
        try {
            return Integer.parseInt(value) > 5;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
