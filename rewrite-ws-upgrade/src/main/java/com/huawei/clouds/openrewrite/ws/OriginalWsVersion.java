package com.huawei.clouds.openrewrite.ws;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries the selected declaration version across the strict-upgrade/recommended-recipe boundary without printing. */
final class OriginalWsVersion implements Marker {
    private final UUID id;
    private final String version;

    OriginalWsVersion(UUID id, String version) {
        this.id = id;
        this.version = version;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public OriginalWsVersion withId(UUID id) {
        return new OriginalWsVersion(id, version);
    }

    String getVersion() {
        return version;
    }
}
