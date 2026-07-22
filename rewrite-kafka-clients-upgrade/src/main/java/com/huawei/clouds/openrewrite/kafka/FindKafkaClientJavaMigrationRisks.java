package com.huawei.clouds.openrewrite.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;

/** Marks Kafka 4.1 API changes whose replacement requires application intent. */
public final class FindKafkaClientJavaMigrationRisks extends Recipe {
    private static final Map<String, String> TYPE_RISKS = Map.of(
            "org.apache.kafka.clients.producer.internals.DefaultPartitioner",
            "DefaultPartitioner was removed; remove the explicit partitioner or implement Partitioner against the target sticky-partition behavior",
            "org.apache.kafka.clients.producer.UniformStickyPartitioner",
            "UniformStickyPartitioner was removed; use the target default partitioning behavior and verify null/non-null key distribution",
            "org.apache.kafka.common.ConsumerGroupState",
            "ConsumerGroupState is deprecated in favor of GroupState; review all group kinds and unknown/dead-state handling before changing the type"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Kafka client 4.1 Java migrations";
    }

    @Override
    public String getDescription() {
        return "Mark removed Kafka client APIs and changed error behavior that cannot be migrated safely from syntax alone.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (methodOn(m, "org.apache.kafka.clients.admin.Admin", "alterConfigs")) {
                    return SearchResult.found(m, "alterConfigs was removed; convert complete Config maps into explicit incrementalAlterConfigs AlterConfigOp operations");
                }
                if (methodOn(m, "org.apache.kafka.clients.producer.Producer", "sendOffsetsToTransaction") &&
                    m.getMethodType().getParameterTypes().size() == 2 &&
                    isString(m.getMethodType().getParameterTypes().get(1))) {
                    return SearchResult.found(m, "The consumerGroupId overload was removed; obtain the matching ConsumerGroupMetadata before choosing the replacement");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions", "topicPartitions")) {
                    return SearchResult.found(m, "topicPartitions was removed; move the partition selection to the Map argument of listConsumerGroupOffsets");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.FeatureUpdate", "allowDowngrade")) {
                    return SearchResult.found(m, "allowDowngrade was removed; choose an explicit FeatureUpdate.UpgradeType");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.Admin", "describeConsumerGroups")) {
                    return SearchResult.found(m, "Missing groups now fail with GroupIdNotFoundException instead of returning a DEAD group; review error handling");
                }
                if (methodOn(m, "org.apache.kafka.clients.admin.Admin", "listConsumerGroups")) {
                    return SearchResult.found(m, "listConsumerGroups is deprecated in 4.1; plan migration to listGroups(ListGroupsOptions.forConsumerGroups()) and verify authorization/filtering");
                }
                if (methodOn(m, "org.apache.kafka.clients.consumer.Consumer", "poll") &&
                    m.getMethodType().getParameterTypes().size() == 1 &&
                    m.getMethodType().getParameterTypes().get(0) == JavaType.Primitive.Long) {
                    return SearchResult.found(m, "Consumer.poll(long) was removed; use poll(Duration), noting that Duration polling does not wait beyond the timeout for initial assignment");
                }
                if (methodOn(m, "org.apache.kafka.clients.consumer.Consumer", "committed") &&
                    !m.getMethodType().getParameterTypes().isEmpty() &&
                    TypeUtils.isOfClassType(m.getMethodType().getParameterTypes().get(0),
                            "org.apache.kafka.common.TopicPartition")) {
                    return SearchResult.found(m, "Single-partition committed overloads were removed; call the Set<TopicPartition> overload and deliberately unwrap missing offsets");
                }
                if (methodOn(m, "org.apache.kafka.clients.producer.Producer", "flush") && insideSendCallback()) {
                    return SearchResult.found(m, "Kafka 4.1 rejects producer.flush() from a send callback to prevent deadlock; move coordination outside the callback without blocking the sender thread");
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.admin.TopicListing") &&
                    n.getArguments().size() == 2) {
                    return SearchResult.found(n, "The two-argument TopicListing constructor was removed; supply the real topic Uuid");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.admin.FeatureUpdate") &&
                    n.getArguments().size() == 2 && isBoolean(n.getArguments().get(1).getType())) {
                    return SearchResult.found(n, "The boolean allowDowngrade constructor was removed; choose an explicit FeatureUpdate.UpgradeType");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.common.metrics.JmxReporter") &&
                    n.getArguments().size() == 1) {
                    return SearchResult.found(n, "JmxReporter(String) was removed; use the no-arg reporter and configure include/exclude rules");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.admin.DescribeTopicsResult") &&
                    n.getArguments().size() == 1) {
                    return SearchResult.found(n, "The one-Map DescribeTopicsResult constructor was removed; provide both topic-id and topic-name future maps with correct ownership");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.producer.KafkaProducer")) {
                    return SearchResult.found(n, "Producer defaults changed across this path: idempotence is enabled and linger.ms is 5; verify latency, batching, ordering, retries and transaction abort handling");
                }
                if (TypeUtils.isOfClassType(n.getType(), "org.apache.kafka.clients.consumer.KafkaConsumer")) {
                    return SearchResult.found(n, "Verify broker >=2.1, classic versus consumer group protocol, assignor metadata, rebalance callbacks, timeouts and rolling restart behavior");
                }
                return n;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) {
                    return i;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(i.getType());
                if (type == null || !i.getSimpleName().equals(type.getClassName())) {
                    return i;
                }
                for (Map.Entry<String, String> risk : TYPE_RISKS.entrySet()) {
                    if (risk.getKey().equals(type.getFullyQualifiedName())) {
                        return SearchResult.found(i, risk.getValue());
                    }
                }
                return i;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!"onNewBatch".equals(m.getSimpleName())) return m;
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (owner != null && TypeUtils.isAssignableTo(
                        "org.apache.kafka.clients.producer.Partitioner", owner.getType())) {
                    return SearchResult.found(m, "Partitioner.onNewBatch was removed; delete the override and revalidate custom partitioner batching, sticky state and thread safety");
                }
                return m;
            }

            private boolean insideSendCallback() {
                org.openrewrite.Cursor cursor = getCursor().getParent();
                boolean lambda = false;
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.Lambda) lambda = true;
                    if (lambda && cursor.getValue() instanceof J.MethodInvocation call &&
                        methodOn(call, "org.apache.kafka.clients.producer.Producer", "send")) return true;
                    if (cursor.getValue() instanceof J.MethodDeclaration ||
                        cursor.getValue() instanceof J.ClassDeclaration) return false;
                    cursor = cursor.getParent();
                }
                return false;
            }
        };
    }

    private static boolean methodOn(J.MethodInvocation method, String owner, String name) {
        return name.equals(method.getSimpleName()) && method.getMethodType() != null &&
               TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }

    private static boolean isString(JavaType type) {
        return TypeUtils.isOfClassType(type, "java.lang.String");
    }

    private static boolean isBoolean(JavaType type) {
        return type == JavaType.Primitive.Boolean || TypeUtils.isOfClassType(type, "java.lang.Boolean");
    }
}
