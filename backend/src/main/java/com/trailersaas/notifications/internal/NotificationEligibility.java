package com.trailersaas.notifications.internal;

import com.trailersaas.identity.NotificationRecipientAccess;
import com.trailersaas.notifications.AlertRecipientPolicy;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class NotificationEligibility {
 final JdbcTemplate jdbc;final NotificationRecipientAccess access;
 NotificationEligibility(JdbcTemplate jdbc,NotificationRecipientAccess access){this.jdbc=jdbc;this.access=access;}
 AlertRecipientPolicy.Context context(UUID tenant,UUID alert){return jdbc.query("""
  select a.related_entity_id,a.alert_type,t.designated_customer_id from alerts a
  join trailers t on t.tenant_id=a.tenant_id and t.id=a.related_entity_id
  where a.tenant_id=? and a.id=?
  """,(r,n)->new AlertRecipientPolicy.Context(tenant,alert,r.getObject(1,UUID.class),r.getObject(3,UUID.class),r.getString(2)),tenant,alert).stream().findFirst().orElseThrow(AlertFailure::missing);}
 boolean authorized(AlertRecipientPolicy.Context c,UUID user){
  if(!"ACTIVE".equals(jdbc.queryForObject("select status from tenants where id=?",String.class,c.tenantId())))return false;
  var scope=access.scope(c.tenantId(),user);return scope.isPresent()&&(scope.get().tenantWide()||scope.get().customerIds().contains(c.designatedCustomerId()));
 }
}
