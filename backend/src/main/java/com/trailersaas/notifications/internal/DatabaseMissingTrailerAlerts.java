package com.trailersaas.notifications.internal;

import com.trailersaas.notifications.MissingTrailerAlerts;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
class DatabaseMissingTrailerAlerts implements MissingTrailerAlerts {
  private final JdbcTemplate jdbc;
  DatabaseMissingTrailerAlerts(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public UUID open(Context c) {
    var id=UUID.randomUUID();
    jdbc.update("""
        insert into alerts(id,tenant_id,alert_type,severity,status,related_entity_type,related_entity_id,message_data,created_at,client_operation_id)
        values(?,?,'TRAILER_MISSING','WARNING','OPEN','TRAILER',?,
          jsonb_build_object('yardCheckId',?::uuid,'itemId',?::uuid,'movementEventId',?::uuid,'actorUserId',?::uuid),clock_timestamp(),?)
        """,id,c.tenantId(),c.trailerId(),c.yardCheckId(),c.itemId(),c.movementEventId(),c.actorUserId(),c.clientOperationId());
    return id;
  }
}
