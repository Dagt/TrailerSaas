package com.trailersaas.trips.internal;

import com.trailersaas.trips.EvidenceWorkDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional(propagation=Propagation.MANDATORY)
class DatabaseEvidenceWorkDirectory implements EvidenceWorkDirectory {
  private final JdbcTemplate jdbc;
  DatabaseEvidenceWorkDirectory(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public Optional<Trip> trip(UUID tenant,UUID trip,boolean exclusive) {
    return findTrip(tenant,trip,exclusive?" FOR UPDATE":" FOR SHARE");
  }
  public Optional<Trip> readTrip(UUID tenant,UUID trip) { return findTrip(tenant,trip,""); }
  private Optional<Trip> findTrip(UUID tenant,UUID trip,String lock) {
    return jdbc.query("SELECT id,current_driver_id,current_tractor_id,status FROM trips WHERE tenant_id=? AND id=?"+lock,
        (r,n)->new Trip(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4)),tenant,trip).stream().findFirst();
  }
  public Optional<Stop> stop(UUID tenant,UUID stop,boolean exclusive) {
    var parents=jdbc.query("SELECT trip_id FROM trip_stops WHERE tenant_id=? AND id=?",(r,n)->r.getObject(1,UUID.class),tenant,stop);
    if(parents.isEmpty()) return Optional.empty();
    var parent=trip(tenant,parents.getFirst(),exclusive);if(parent.isEmpty()) return Optional.empty();
    return jdbc.query("""
        SELECT s.id,s.stop_type,s.status,s.version,s.completed_at,
          (SELECT e.actor_user_id FROM trip_stop_events e WHERE e.tenant_id=s.tenant_id AND e.trip_stop_id=s.id
            AND e.event_type='COMPLETED' AND e.actor_type='HUMAN_USER' AND e.occurred_at=s.completed_at
            ORDER BY e.recorded_at DESC,e.id DESC LIMIT 1) completion_actor,
          (s.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296)
            AND EXISTS(SELECT 1 FROM trip_stop_events e WHERE e.tenant_id=s.tenant_id AND e.trip_stop_id=s.id
              AND e.event_type='COMPLETED' AND e.occurred_at=s.completed_at
              AND e.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296))) completion_in_tx
        FROM trip_stops s WHERE s.tenant_id=? AND s.id=? AND s.trip_id=?
        """,(r,n)->new Stop(r.getObject(1,UUID.class),parent.get(),r.getString(2),r.getString(3),r.getLong(4),
          r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),r.getObject(6,UUID.class),r.getBoolean(7)),tenant,stop,parent.get().id()).stream().findFirst();
  }
}
