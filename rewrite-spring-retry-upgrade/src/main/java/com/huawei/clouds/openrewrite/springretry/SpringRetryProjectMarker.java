package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries exact pre-upgrade project eligibility to later recipe stages. */
final class SpringRetryProjectMarker implements Marker {
    private final UUID id;

    SpringRetryProjectMarker(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends Marker> M withId(UUID id) {
        return (M) new SpringRetryProjectMarker(id);
    }
}
