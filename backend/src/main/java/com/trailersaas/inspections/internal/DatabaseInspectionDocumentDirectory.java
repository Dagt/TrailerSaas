package com.trailersaas.inspections.internal;

import com.trailersaas.documents.InspectionDocumentDirectory;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository @Transactional(propagation=Propagation.MANDATORY)
class DatabaseInspectionDocumentDirectory implements InspectionDocumentDirectory {
  private final JdbcTemplate jdbc;
  DatabaseInspectionDocumentDirectory(JdbcTemplate jdbc) {this.jdbc=jdbc;}
  public Optional<Context> find(UUID tenant,UUID id) {
    return jdbc.query("""
      SELECT i.driver_id,d.user_id,i.tractor_id,i.trip_id,i.inspection_type,s.off_duty_at IS NULL,
        i.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296)
      FROM inspections i JOIN drivers d ON d.tenant_id=i.tenant_id AND d.id=i.driver_id
      JOIN shifts s ON s.tenant_id=i.tenant_id AND s.id=i.shift_id WHERE i.tenant_id=? AND i.id=?
      """,(r,n)->new Context(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getString(5),r.getBoolean(6),r.getBoolean(7)),tenant,id).stream().findFirst();
  }
}
