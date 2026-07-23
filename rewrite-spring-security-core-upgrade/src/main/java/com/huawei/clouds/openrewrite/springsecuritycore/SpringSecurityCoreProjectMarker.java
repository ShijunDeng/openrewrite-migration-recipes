package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries exact pre-upgrade project eligibility to later migration recipes. */
final class SpringSecurityCoreProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    SpringSecurityCoreProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public SpringSecurityCoreProjectMarker withId(UUID id) {
        return new SpringSecurityCoreProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
