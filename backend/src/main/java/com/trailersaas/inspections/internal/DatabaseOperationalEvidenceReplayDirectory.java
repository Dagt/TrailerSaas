package com.trailersaas.inspections.internal;

import com.trailersaas.documents.OperationalEvidenceReplayDirectory;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository @Transactional(propagation=Propagation.MANDATORY)
class DatabaseOperationalEvidenceReplayDirectory implements OperationalEvidenceReplayDirectory {
  private final JdbcTemplate jdbc;
  DatabaseOperationalEvidenceReplayDirectory(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public boolean fuelCommitted(UUID tenant,UUID user,UUID operation,UUID trip){
    return Boolean.TRUE.equals(jdbc.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM processed_client_operations p JOIN fuel_events f ON f.tenant_id=p.tenant_id AND f.id=p.result_entity_id
      WHERE p.tenant_id=? AND p.client_operation_id=? AND p.user_id=? AND p.operation_type='FUEL_EVENT' AND p.status='SUCCEEDED' AND f.trip_id=?)
      """,Boolean.class,tenant,operation,user,trip));
  }
  public boolean damageCommitted(UUID tenant,UUID user,UUID operation,UUID trip){
    return Boolean.TRUE.equals(jdbc.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM processed_client_operations p JOIN operational_exceptions e ON e.tenant_id=p.tenant_id AND e.id=p.result_entity_id
      WHERE p.tenant_id=? AND p.client_operation_id=? AND p.user_id=? AND p.operation_type='OPERATIONAL_EXCEPTION' AND p.status='SUCCEEDED' AND e.trip_id=?)
      """,Boolean.class,tenant,operation,user,trip));
  }
}
