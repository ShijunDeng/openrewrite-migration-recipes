package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries upgrade-time project eligibility to later source and configuration recipes. */
final class SpringWebMvcProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    SpringWebMvcProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public SpringWebMvcProjectMarker withId(UUID id) {
        return new SpringWebMvcProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
