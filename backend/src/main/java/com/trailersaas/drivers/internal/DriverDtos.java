package com.trailersaas.drivers.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

final class DriverDtos {
    private DriverDtos() {}
    record Items(List<Driver> items) { Items { items=List.copyOf(items); } }
    record Driver(UUID id, UUID userId, String name, String licenseNumber, LocalDate medicalCertificateExpires,
            boolean active, long version, Instant createdAt, Instant updatedAt) {
        static Driver of(DriverEntity e) {
            return new Driver(e.id, e.userId,e.name,e.licenseNumber,e.medicalCertificateExpires,
                    e.active,e.version,e.createdAt,e.updatedAt);
        }
    }
}
