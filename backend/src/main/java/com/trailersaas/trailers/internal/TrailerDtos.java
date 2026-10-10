package com.trailersaas.trailers.internal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class TrailerDtos {
    private TrailerDtos() {}
    record Items(List<Trailer> items) { Items { items=List.copyOf(items); } }
    record QrItems(List<Qr> items) { QrItems { items=List.copyOf(items); } }
    record Trailer(UUID id,String operationalNumber,UUID designatedCustomerId,String trailerType,String capabilityNotes,
            String locationState,UUID currentLocationId,UUID lastKnownLocationId,Instant lastSeenAt,UUID lastDriverId,
            String loadState,String serviceabilityState,String usagePurpose,boolean active,long version,Instant createdAt,Instant updatedAt) {
        static Trailer of(TrailerEntity e) {
            return new Trailer(e.id,e.operationalNumber,e.designatedCustomerId,e.trailerType,e.capabilityNotes,e.locationState,
                    e.currentLocationId,e.lastKnownLocationId,e.lastSeenAt,e.lastDriverId,e.loadState,e.serviceabilityState,
                    e.usagePurpose,e.active,e.version,e.createdAt,e.updatedAt);
        }
    }
    record Qr(UUID id,UUID trailerId,String publicToken,String operationalNumber,boolean active,Instant issuedAt,Instant revokedAt) {
        static Qr of(TrailerQrEntity q,TrailerEntity t) { return new Qr(q.id,q.trailerId,q.publicToken,t.operationalNumber,q.active,q.issuedAt,q.revokedAt); }
        @Override public String toString() { return "Qr[id="+id+", publicToken=[redacted]]"; }
    }
    record Scan(UUID trailerId,String operationalNumber,String trailerType,String locationState,String loadState,String serviceabilityState,boolean active,long version) {
        static Scan of(TrailerEntity t) { return new Scan(t.id,t.operationalNumber,t.trailerType,t.locationState,t.loadState,t.serviceabilityState,t.active,t.version); }
    }
}
