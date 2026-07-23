package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/**
 * Non-printing evidence that the nearest build root owned one unambiguous,
 * workbook-approved Jetty HTTP source version before dependency changes ran.
 */
final class JettyHttpProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    JettyHttpProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public JettyHttpProjectMarker withId(UUID id) {
        return new JettyHttpProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
