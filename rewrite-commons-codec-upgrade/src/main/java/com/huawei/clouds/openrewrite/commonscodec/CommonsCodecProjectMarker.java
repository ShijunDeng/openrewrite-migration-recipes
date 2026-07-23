package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Non-printing proof that the nearest build root owns one exact workbook source version. */
final class CommonsCodecProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    CommonsCodecProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public CommonsCodecProjectMarker withId(UUID id) {
        return new CommonsCodecProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
