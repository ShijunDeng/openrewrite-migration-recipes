package com.huawei.clouds.openrewrite.junrar;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries the exact pre-upgrade Junrar source version across the recipe tree. */
final class JunrarProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    JunrarProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public JunrarProjectMarker withId(UUID id) {
        return new JunrarProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
