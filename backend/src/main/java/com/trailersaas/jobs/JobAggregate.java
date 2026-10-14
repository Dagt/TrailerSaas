package com.trailersaas.jobs;

import java.util.Objects;
import java.util.UUID;

public record JobAggregate(String type, UUID id) {
    public JobAggregate {
        Objects.requireNonNull(id, "Aggregate ID required");
        if (type == null || !type.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid aggregate type");
    }
}
