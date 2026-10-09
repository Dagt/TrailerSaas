package com.trailersaas.customers.internal;

import com.trailersaas.customers.TripDestinationPolicy;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseTripDestinationPolicy implements TripDestinationPolicy {
  private final EntityManager em;
  private final DatabaseOrderDestinationPolicy orders;

  DatabaseTripDestinationPolicy(EntityManager em, DatabaseOrderDestinationPolicy orders) {
    this.em = em;
    this.orders = orders;
  }

  public Optional<Map<String, Object>> snapshot(UUID tenant, UUID site) {
    return em.createQuery(
            "select s from PhysicalSiteEntity s where s.tenantId=:tenant and s.id=:site",
            PhysicalSiteEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("site", site)
        .setLockMode(LockModeType.PESSIMISTIC_READ)
        .getResultStream()
        .findFirst()
        .map(
            s -> {
              var data = new LinkedHashMap<String, Object>();
              data.put("name", s.name);
              data.put("timezone", s.timezone);
              var address = new LinkedHashMap<String, Object>();
              put(address, "line1", s.line1);
              put(address, "line2", s.line2);
              put(address, "city", s.city);
              put(address, "stateRegion", s.stateRegion);
              put(address, "postalCode", s.postalCode);
              put(address, "countryCode", s.countryCode);
              if (!address.isEmpty()) data.put("address", Collections.unmodifiableMap(address));
              put(data, "latitude", s.latitude);
              put(data, "longitude", s.longitude);
              put(data, "geofenceRadiusM", s.geofenceRadiusM);
              return Collections.unmodifiableMap(data);
            });
  }

  private static void put(Map<String, Object> data, String key, Object value) {
    if (value != null) data.put(key, value);
  }

  public boolean compatible(UUID tenant, Set<UUID> customers, UUID site, UUID location) {
    if (snapshot(tenant, site).isEmpty()) return false;
    if (location != null
        && em.createNativeQuery(
                "select id from operational_locations where tenant_id=:tenant and site_id=:site and"
                    + " id=:location for share",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("site", site)
            .setParameter("location", location)
            .setFlushMode(FlushModeType.COMMIT)
            .getResultList()
            .isEmpty()) {
      return false;
    }
    if (!customers.isEmpty()) {
      return customers.stream()
          .sorted()
          .allMatch(c -> orders.compatible(tenant, c, site, location));
    }
    var found =
        em.createNativeQuery(
                "select id from physical_sites where tenant_id=:tenant and id=:site for share",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("site", site)
            .setFlushMode(FlushModeType.COMMIT)
            .getResultList();
    if (found.isEmpty()) {
      return false;
    }
    return location == null
        || !em.createQuery(
                "select l.id from OperationalLocationEntity l where l.tenantId=:tenant and"
                    + " l.siteId=:site and l.id=:location and l.customerId is null",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("site", site)
            .setParameter("location", location)
            .getResultList()
            .isEmpty();
  }
}
