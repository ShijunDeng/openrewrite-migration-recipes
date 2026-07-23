package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Prevents a conflict-protected source file from being transiently edited again in the same run. */
final class SpringRetryAnnotationConflictHandledMarker implements Marker {
    private final UUID id;

    SpringRetryAnnotationConflictHandledMarker(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Marker> M withId(UUID id) {
        return (M) new SpringRetryAnnotationConflictHandledMarker(id);
    }
}
