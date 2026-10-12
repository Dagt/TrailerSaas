package com.trailersaas.inspections.internal;

import com.trailersaas.drivers.DriverShiftContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Bounded own-home SQL read model; no exclusive locks or event replay. */
@Service @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
class DatabaseDriverShiftContext implements DriverShiftContext {
  private final JdbcTemplate jdbc;
  private final ShiftRepository shifts;
  DatabaseDriverShiftContext(JdbcTemplate jdbc,ShiftRepository shifts) {this.jdbc=jdbc;this.shifts=shifts;}
  public Home read(UUID tenant,UUID driver) {
    var shift=shifts.open(tenant,driver);
    var inspections=shift==null?List.<Inspection>of():jdbc.query("SELECT * FROM inspections WHERE tenant_id=? AND driver_id=? AND shift_id=? ORDER BY occurred_at DESC,id DESC LIMIT 50",
      (r,n)->new Inspection(r.getObject("id",UUID.class),r.getObject("shift_id",UUID.class),r.getObject("trip_id",UUID.class),r.getObject("tractor_id",UUID.class),r.getString("inspection_type"),r.getBigDecimal("odometer"),ShiftRepository.instant(r,"occurred_at"),ShiftRepository.instant(r,"created_at")),tenant,driver,shift.id());
    var equipment=jdbc.query("""
      SELECT t.current_tractor_id,a.operational_number,t.id FROM trips t JOIN tractors a ON a.tenant_id=t.tenant_id AND a.id=t.current_tractor_id AND a.active
      WHERE t.tenant_id=? AND t.current_driver_id=? AND t.status NOT IN ('COMPLETED','CANCELLED') ORDER BY a.operational_number,t.id LIMIT 50
      """,(r,n)->new Equipment(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class)),tenant,driver);
    return new Home(shift,inspections,equipment,shift==null?List.of():shifts.blockers(tenant,driver,shift));
  }
}
