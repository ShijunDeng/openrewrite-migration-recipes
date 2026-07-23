package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.marker.Marker;

import java.util.Map;
import java.util.UUID;

/** Ephemeral identity proving that the current recipe run protected an annotation. */
final class SpringRetryAnnotationProtectionMarker implements Marker {
    private final UUID id;
    private final Map<UUID, String> protectedAssignments;

    SpringRetryAnnotationProtectionMarker(UUID id, Map<UUID, String> protectedAssignments) {
        this.id = id;
        this.protectedAssignments = Map.copyOf(protectedAssignments);
    }

    @Override
    public UUID getId() {
        return id;
    }

    Map<UUID, String> getProtectedAssignments() {
        return protectedAssignments;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Marker> M withId(UUID id) {
        return (M) new SpringRetryAnnotationProtectionMarker(id, protectedAssignments);
    }
}
