package com.trailersaas.notifications.internal;

import com.trailersaas.jobs.*;
import com.trailersaas.notifications.AlertNotifications;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
class DatabaseAlertNotifications implements AlertNotifications {
 final JdbcTemplate jdbc;final DurableJobPublisher publisher;final RecipientPolicies policies;final NotificationEligibility eligibility;
 DatabaseAlertNotifications(JdbcTemplate jdbc,DurableJobPublisher publisher,RecipientPolicies policies,NotificationEligibility eligibility){this.jdbc=jdbc;this.publisher=publisher;this.policies=policies;this.eligibility=eligibility;}
 public List<UUID> requestDelivery(UUID tenant,UUID alert,String code){
  var context=eligibility.context(tenant,alert);
  if("RESOLVED".equals(jdbc.queryForObject("select status from alerts where tenant_id=? and id=?",String.class,tenant,alert)))throw AlertFailure.state();
  var ids=new ArrayList<UUID>();
  for(var user:policies.candidates(code,context)){
   if(!eligibility.authorized(context,user))throw new IllegalArgumentException("Recipient is not authorized");
   var id=UUID.randomUUID();
   int inserted=jdbc.update("insert into alert_recipients(id,tenant_id,alert_id,user_id,policy_code) values(?,?,?,?,?) on conflict(tenant_id,alert_id,user_id) do nothing",id,tenant,alert,user,code);
   if(inserted==1){publisher.enqueue(tenant,AlertDeliveryHandler.TYPE,new JobAggregate("ALERT",alert),new AlertDeliveryHandler.Payload(id),Instant.now(),null);ids.add(id);}
  }
  return List.copyOf(ids);
 }
}
