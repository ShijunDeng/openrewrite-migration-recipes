package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Non-printing proof that the nearest build root owns one exact workbook source version. */
final class JUnitJupiterAggregateProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    JUnitJupiterAggregateProjectMarker(UUID id, String sourceVersion) {
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
        return (M) new JUnitJupiterAggregateProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
