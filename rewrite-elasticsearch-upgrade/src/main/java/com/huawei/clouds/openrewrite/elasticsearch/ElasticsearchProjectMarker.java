package com.huawei.clouds.openrewrite.elasticsearch;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries exact pre-upgrade build-root eligibility to later build and source recipes. */
final class ElasticsearchProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    ElasticsearchProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public ElasticsearchProjectMarker withId(UUID id) {
        return new ElasticsearchProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
