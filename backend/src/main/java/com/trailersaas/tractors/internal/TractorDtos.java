package com.trailersaas.tractors.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


final class TractorDtos {
    private TractorDtos() {}
    record Items(List<Tractor> items) { Items { items=List.copyOf(items); } }
    record Tractor(UUID id, String operationalNumber,
            boolean active, long version, Instant createdAt, Instant updatedAt) {
        static Tractor of(TractorEntity e) {
            return new Tractor(e.id, e.operationalNumber,
                    e.active,e.version,e.createdAt,e.updatedAt);
        }
    }
}
