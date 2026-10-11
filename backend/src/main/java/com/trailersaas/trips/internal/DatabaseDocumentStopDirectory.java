package com.trailersaas.trips.internal;

import com.trailersaas.trips.DocumentStopDirectory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
class DatabaseDocumentStopDirectory implements DocumentStopDirectory {
  private final JdbcTemplate jdbc;
  DatabaseDocumentStopDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Optional<Stop> find(UUID tenant, UUID stop, boolean lockParent) {
    var rows = jdbc.query("""
        SELECT s.id,t.id trip_id,t.current_driver_id FROM trip_stops s
        JOIN trips t ON t.tenant_id=s.tenant_id AND t.id=s.trip_id
        WHERE s.tenant_id=? AND s.id=?
        """ + (lockParent ? " FOR SHARE OF t" : ""),
        (r,n) -> new Stop(r.getObject("id",UUID.class),r.getObject("trip_id",UUID.class),
            r.getObject("current_driver_id",UUID.class),Set.of()), tenant,stop);
    if (rows.isEmpty()) return Optional.empty();
    var parent=rows.getFirst();
    var customers=new HashSet<>(jdbc.query("""
        SELECT DISTINCT o.customer_id FROM trip_stop_orders m
        JOIN orders o ON o.tenant_id=m.tenant_id AND o.id=m.order_id
        WHERE m.tenant_id=? AND m.trip_stop_id=?
        """, (r,n)->r.getObject(1,UUID.class),tenant,stop));
    return Optional.of(new Stop(parent.id(),parent.tripId(),parent.driverId(),customers));
  }
}
