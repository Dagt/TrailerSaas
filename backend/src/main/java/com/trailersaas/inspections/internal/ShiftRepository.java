package com.trailersaas.inspections.internal;

import com.trailersaas.drivers.DriverShiftContext;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository @Transactional(propagation=Propagation.MANDATORY)
class ShiftRepository {
  private final JdbcTemplate jdbc;
  ShiftRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
  static Instant instant(ResultSet r,String key) throws SQLException {var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
  static DriverShiftContext.Shift shift(ResultSet r,int n) throws SQLException {
    var off=instant(r,"off_duty_at");return new DriverShiftContext.Shift(r.getObject("id",UUID.class),off==null?"ON_DUTY":"OFF_DUTY",instant(r,"on_duty_at"),off,instant(r,"created_at"),instant(r,"updated_at"));
  }
  DriverShiftContext.Shift own(UUID tenant,UUID driver,UUID id,boolean lock) {
    return jdbc.query("SELECT * FROM shifts WHERE tenant_id=? AND driver_id=? AND id=?"+(lock?" FOR UPDATE":""),ShiftRepository::shift,tenant,driver,id).stream().findFirst().orElseThrow(ShiftFailure::missing);
  }
  DriverShiftContext.Shift open(UUID tenant,UUID driver) {
    return jdbc.query("SELECT * FROM shifts WHERE tenant_id=? AND driver_id=? AND off_duty_at IS NULL",ShiftRepository::shift,tenant,driver).stream().findFirst().orElse(null);
  }
  DriverShiftContext.Shift create(UUID tenant,UUID driver,Instant at) {
    if(open(tenant,driver)!=null) throw ShiftFailure.conflict();
    if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM shifts WHERE tenant_id=? AND driver_id=? AND off_duty_at>?)",Boolean.class,tenant,driver,Timestamp.from(at))) throw ShiftFailure.state();
    UUID id=UUID.randomUUID();Instant recorded=Instant.now();jdbc.update("INSERT INTO shifts VALUES(?,?,?, ?,NULL,?,?)",id,tenant,driver,Timestamp.from(at),Timestamp.from(recorded),Timestamp.from(recorded));
    return own(tenant,driver,id,false);
  }
  List<String> blockers(UUID tenant,UUID driver,DriverShiftContext.Shift shift) {
    var reasons=new ArrayList<String>();
    if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM trips WHERE tenant_id=? AND current_driver_id=? AND status NOT IN ('COMPLETED','CANCELLED'))",Boolean.class,tenant,driver)) reasons.add("ACTIVE_WORK");
    if(shift!=null && jdbc.queryForObject("""
      WITH equipment AS (
        SELECT tractor_id,max(occurred_at) latest FROM inspections WHERE tenant_id=? AND shift_id=? AND inspection_type='PRE_TRIP' GROUP BY tractor_id
        UNION ALL SELECT current_tractor_id,max(completed_at) FROM trips WHERE tenant_id=? AND current_driver_id=? AND started_at>=? AND current_tractor_id IS NOT NULL GROUP BY current_tractor_id
      ), used AS (SELECT tractor_id,max(latest) latest FROM equipment GROUP BY tractor_id)
      SELECT EXISTS(SELECT 1 FROM used u WHERE NOT EXISTS(SELECT 1 FROM inspections i WHERE i.tenant_id=? AND i.shift_id=? AND i.tractor_id=u.tractor_id AND i.inspection_type='POST_TRIP' AND i.occurred_at>=u.latest))
      """,Boolean.class,tenant,shift.id(),tenant,driver,Timestamp.from(shift.onDutyAt()),tenant,shift.id())) reasons.add("POST_TRIP_REQUIRED");
    return reasons;
  }
  DriverShiftContext.Shift close(UUID tenant,UUID driver,UUID id,Instant at) {
    var shift=own(tenant,driver,id,true);
    if(!"ON_DUTY".equals(shift.state()) || at.isBefore(shift.onDutyAt())) throw ShiftFailure.state();
    if(!blockers(tenant,driver,shift).isEmpty()) throw ShiftFailure.conflict();
    if(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM inspections WHERE tenant_id=? AND shift_id=? AND occurred_at>?)",Boolean.class,tenant,id,Timestamp.from(at))) throw ShiftFailure.state();
    if(jdbc.update("UPDATE shifts SET off_duty_at=?,updated_at=? WHERE tenant_id=? AND driver_id=? AND id=? AND off_duty_at IS NULL",Timestamp.from(at),Timestamp.from(Instant.now()),tenant,driver,id)!=1) throw ShiftFailure.state();
    return own(tenant,driver,id,false);
  }
}
