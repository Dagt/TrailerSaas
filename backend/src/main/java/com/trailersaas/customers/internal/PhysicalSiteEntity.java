package com.trailersaas.customers.internal;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "physical_sites")
class PhysicalSiteEntity extends VersionedMasterData {
    @Column(name = "address_line1") String line1;
    @Column(name = "address_line2") String line2;
    String city;
    @Column(name = "state_region") String stateRegion;
    @Column(name = "postal_code") String postalCode;
    @Column(name = "country_code", length = 2) String countryCode;
    @Column(nullable = false) String timezone;
    @Column(precision = 9, scale = 6) BigDecimal latitude;
    @Column(precision = 9, scale = 6) BigDecimal longitude;
    @Column(name = "geofence_radius_m") Integer geofenceRadiusM;
    @Column(nullable = false) boolean active = true;
    protected PhysicalSiteEntity() {}
}
