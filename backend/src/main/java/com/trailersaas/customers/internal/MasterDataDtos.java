package com.trailersaas.customers.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class MasterDataDtos {
    private MasterDataDtos() {}
    record CreateCustomer(String name) {}
    record Address(String line1, String line2, String city, String stateRegion, String postalCode, String countryCode) {}
    record CreateSite(String name, Address address, String timezone, BigDecimal latitude,
            BigDecimal longitude, Integer geofenceRadiusM) {}
    record AssociateSite(UUID siteId) {}
    record CreateLocation(String name, String type, String code, UUID customerId) {}
    record Items<T>(List<T> items) { Items { items = List.copyOf(items); } }
    record Customer(UUID id, String name, String status, long version, Instant createdAt, Instant updatedAt) {
        static Customer of(CustomerEntity e) {
            return new Customer(e.id, e.name, e.status, e.version, e.createdAt, e.updatedAt);
        }
    }
    record Site(UUID id, String name, Address address, String timezone, BigDecimal latitude,
            BigDecimal longitude, Integer geofenceRadiusM, boolean active,
            long version, Instant createdAt, Instant updatedAt) {
        static Site of(PhysicalSiteEntity e) {
            return new Site(e.id, e.name, new Address(e.line1, e.line2, e.city, e.stateRegion, e.postalCode, e.countryCode),
                    e.timezone, e.latitude, e.longitude, e.geofenceRadiusM, e.active, e.version, e.createdAt, e.updatedAt);
        }
    }
    record Location(UUID id, UUID siteId, UUID customerId, String name, String type, String code,
            boolean active, long version, Instant createdAt, Instant updatedAt) {
        static Location of(OperationalLocationEntity e) {
            return new Location(e.id, e.siteId, e.customerId, e.name, e.type, e.code,
                    e.active, e.version, e.createdAt, e.updatedAt);
        }
    }
    record CustomerSite(UUID id, UUID customerId, UUID siteId, Instant createdAt) {
        static CustomerSite of(CustomerSiteEntity e) {
            return new CustomerSite(e.id, e.customerId, e.siteId, e.createdAt);
        }
    }
}
