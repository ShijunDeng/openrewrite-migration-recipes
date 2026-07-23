package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/**
 * Non-printing evidence that the nearest build root owned one exact,
 * non-conflicting workbook source version before dependency edits.
 */
final class SpringSecurityWebProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    SpringSecurityWebProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public SpringSecurityWebProjectMarker withId(UUID id) {
        return new SpringSecurityWebProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
