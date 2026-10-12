package com.trailersaas.inspections.internal;

import com.trailersaas.trips.TripDeparturePrerequisites;
import java.time.Instant;
import java.util.UUID;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Called under execution's Trip/Driver locks; no reverse dependency or lock upgrade. */
@Service @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
public class DatabaseTripDeparturePrerequisites implements TripDeparturePrerequisites {
  private final JdbcTemplate jdbc;
  public DatabaseTripDeparturePrerequisites(JdbcTemplate jdbc) {this.jdbc=jdbc;}
  public boolean satisfied(UUID tenant,UUID driver,UUID tractor,UUID trip) {return satisfiedAt(tenant,driver,tractor,trip,Instant.now());}
  public boolean satisfiedAt(UUID tenant,UUID driver,UUID tractor,UUID trip,Instant at) {
    if(tenant==null || driver==null || tractor==null || trip==null || at==null)return false;
    return jdbc.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM shifts s JOIN inspections i ON i.tenant_id=s.tenant_id AND i.shift_id=s.id AND i.driver_id=s.driver_id
        JOIN drivers d ON d.tenant_id=s.tenant_id AND d.id=s.driver_id AND d.active
        JOIN tractors a ON a.tenant_id=i.tenant_id AND a.id=i.tractor_id AND a.active
        JOIN trips t ON t.tenant_id=i.tenant_id AND t.id=? AND t.current_driver_id=s.driver_id AND t.current_tractor_id=i.tractor_id
        WHERE s.tenant_id=? AND s.driver_id=? AND s.off_duty_at IS NULL AND s.on_duty_at<=? AND i.tractor_id=?
          AND i.inspection_type='PRE_TRIP' AND (i.trip_id IS NULL OR i.trip_id=t.id) AND i.occurred_at>=s.on_duty_at AND i.occurred_at<=?
          AND (SELECT count(DISTINCT doc.document_type) FROM document_links l JOIN documents doc ON doc.tenant_id=l.tenant_id AND doc.id=l.document_id
            WHERE l.tenant_id=i.tenant_id AND l.entity_type='INSPECTION' AND l.entity_id=i.id AND doc.upload_status='UPLOADED' AND doc.uploaded_by_user_id=d.user_id
            AND doc.document_type IN ('PRETRIP_FRONT','PRETRIP_REAR','PRETRIP_DRIVER_SIDE','PRETRIP_PASSENGER_SIDE')
            AND NOT EXISTS(SELECT 1 FROM document_links extra WHERE extra.tenant_id=doc.tenant_id AND extra.document_id=doc.id
              AND NOT (extra.entity_type='INSPECTION' AND extra.entity_id=i.id OR extra.entity_type='TRIP' AND i.trip_id IS NOT NULL AND extra.entity_id=i.trip_id)))=4)
      """,Boolean.class,trip,tenant,driver,Timestamp.from(at),tractor,Timestamp.from(at));
  }
}
