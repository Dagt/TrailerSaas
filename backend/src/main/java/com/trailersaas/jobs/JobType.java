package com.trailersaas.jobs;

import java.util.Objects;

/** Closed, versioned payload record; aggregateType null explicitly means no aggregate. */
public record JobType<P>(String name, int schemaVersion, Class<P> payloadClass, String aggregateType) {
    public JobType {
        if (name == null || !name.matches("[A-Z][A-Z0-9_]{0,63}") || schemaVersion < 1 || schemaVersion > 999
                || !Objects.requireNonNull(payloadClass).isRecord()
                || (aggregateType != null && !aggregateType.matches("[A-Z][A-Z0-9_]{0,63}"))) {
            throw new IllegalArgumentException("Invalid job type definition");
        }
    }
}
