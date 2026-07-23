package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Non-printing project eligibility carried from build scanning to later source/configuration recipes. */
final class SpringBootProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    SpringBootProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public SpringBootProjectMarker withId(UUID id) {
        return new SpringBootProjectMarker(id, sourceVersion);
    }

    boolean isSelectedSource() {
        return true;
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
