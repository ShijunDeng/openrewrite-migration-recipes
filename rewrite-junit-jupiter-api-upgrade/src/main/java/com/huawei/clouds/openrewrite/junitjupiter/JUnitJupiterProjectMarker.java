package com.huawei.clouds.openrewrite.junitjupiter;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Non-printing proof that the nearest build root owns one exact source version. */
final class JUnitJupiterProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    JUnitJupiterProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Marker> M withId(UUID id) {
        return (M) new JUnitJupiterProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
