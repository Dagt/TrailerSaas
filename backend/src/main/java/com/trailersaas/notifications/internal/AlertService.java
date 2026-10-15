package com.trailersaas.notifications.internal;

import com.trailersaas.identity.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
class AlertService {
 final JdbcTemplate jdbc; final TenantAuthorization authorization; final TrailerAccessPolicy trailers;
 AlertService(JdbcTemplate jdbc,TenantAuthorization authorization,TrailerAccessPolicy trailers){this.jdbc=jdbc;this.authorization=authorization;this.trailers=trailers;}
 record Resource(UUID id,String operationalNumber,UUID designatedCustomerId,String locationState,UUID currentLocationId){}
 record Alert(UUID id,UUID tenantId,String alertType,String severity,String status,long version,Instant createdAt,
  UUID acknowledgedBy,Instant acknowledgedAt,UUID resolvedBy,Instant resolvedAt,String resolutionReason,
  String relatedEntityType,Resource relatedResource,Map<String,String> context,Map<String,Long> pushSummary){}
 record Page(List<Alert> items,String nextCursor){}
 static final String SELECT="""
  SELECT a.*,t.operational_number,t.designated_customer_id,t.location_state,t.current_location_id
  FROM alerts a JOIN trailers t ON t.tenant_id=a.tenant_id AND t.id=a.related_entity_id
  WHERE a.tenant_id=?
  """;
 CustomerAccessScope access(AuthenticatedUserPrincipal actor,UUID tenant,Capability capability){
  var scope=authorization.resolveCustomerScope(actor,tenant,capability);
  authorization.requireTenantCapability(actor,tenant,Capability.ALERT_VIEW);
  if(!"ACTIVE".equals(jdbc.queryForObject("select status from tenants where id=?",String.class,tenant)))throw AlertFailure.missing();
  return scope;
 }
 @Transactional(readOnly=true) public Page list(AuthenticatedUserPrincipal actor,UUID tenant,Map<String,String> q){
  var scope=access(actor,tenant,Capability.ALERT_VIEW);var resourceScope=trailers.readScope(actor,tenant);
  keys(q,Set.of("status","alertType","severity","relatedEntityType","from","to","cursor","limit"));
  int limit=50;try{if(q.containsKey("limit"))limit=Integer.parseInt(q.get("limit"));}catch(NumberFormatException e){throw AlertFailure.invalid();}
  if(limit<1||limit>100)throw AlertFailure.invalid();
  var sql=new StringBuilder(SELECT);var args=new ArrayList<Object>();args.add(tenant);
  scope(sql,args,scope);scope(sql,args,resourceScope);
  for(var entry:Map.of("status",Set.of("OPEN","ACKNOWLEDGED","RESOLVED"),"alertType",Set.of("TRAILER_MISSING"),"severity",Set.of("WARNING"),"relatedEntityType",Set.of("TRAILER")).entrySet()){
   var value=q.get(entry.getKey());if(value!=null){if(!entry.getValue().contains(value))throw AlertFailure.invalid();sql.append(" AND a.").append(switch(entry.getKey()){case "alertType"->"alert_type";case "relatedEntityType"->"related_entity_type";default->entry.getKey();}).append("=?");args.add(value);}
  }
  Instant from=time(q.get("from")),to=time(q.get("to"));if(from!=null&&to!=null&&from.isAfter(to))throw AlertFailure.invalid();
  if(from!=null){sql.append(" AND a.created_at>=?");args.add(java.sql.Timestamp.from(from));}
  if(to!=null){sql.append(" AND a.created_at<=?");args.add(java.sql.Timestamp.from(to));}
  if(q.containsKey("cursor"))try{
   var pieces=new String(Base64.getUrlDecoder().decode(q.get("cursor")),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);
   if(pieces.length!=2)throw AlertFailure.invalid();var at=Instant.parse(pieces[0]);var id=UUID.fromString(pieces[1]);
   sql.append(" AND (a.created_at,a.id)<(?,?)");args.add(java.sql.Timestamp.from(at));args.add(id);
  }catch(IllegalArgumentException|java.time.DateTimeException e){throw AlertFailure.invalid();}
  sql.append(" ORDER BY a.created_at DESC,a.id DESC LIMIT ?");args.add(limit+1);
  var rows=jdbc.query(sql.toString(),this::row,args.toArray());boolean more=rows.size()>limit;
  var items=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));var last=items.isEmpty()?null:items.getLast();
  return new Page(items,more?Base64.getUrlEncoder().withoutPadding().encodeToString((last.createdAt()+"|"+last.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null);
 }
 @Transactional(readOnly=true) public Alert detail(AuthenticatedUserPrincipal actor,UUID tenant,UUID id){
  var scope=access(actor,tenant,Capability.ALERT_VIEW);var resourceScope=trailers.readScope(actor,tenant);
  var args=new ArrayList<Object>(List.of(tenant,id));var sql=new StringBuilder(SELECT+" AND a.id=?");scope(sql,args,scope);scope(sql,args,resourceScope);
  return jdbc.query(sql.toString(),this::row,args.toArray()).stream().findFirst().orElseThrow(AlertFailure::missing);
 }
 public Alert transition(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,Object> body,boolean resolve){
  access(actor,tenant,resolve?Capability.ALERT_RESOLVE:Capability.ALERT_ACKNOWLEDGE);
  var before=detail(actor,tenant,id); // Fresh permission and current related-resource scope before any locking/write.
  keys(body,resolve?Set.of("expectedVersion","reason"):Set.of("expectedVersion"));
  if(!(body.get("expectedVersion") instanceof Number n)||!Set.of(Integer.class,Long.class).contains(n.getClass())||n.longValue()<0)throw AlertFailure.invalid();
  String reason=null;if(resolve){if(!(body.get("reason") instanceof String s)||s.isBlank()||s.length()>4000)throw AlertFailure.invalid();reason=s.trim();}
  var states=jdbc.queryForList("select status,version from alerts where tenant_id=? and id=? for update",tenant,id);if(states.isEmpty())throw AlertFailure.missing();
  var state=states.getFirst();if(((Number)state.get("version")).longValue()!=n.longValue())throw AlertFailure.stale();
  if(resolve?!Set.of("OPEN","ACKNOWLEDGED").contains(state.get("status")):!"OPEN".equals(state.get("status")))throw AlertFailure.state();
  if(resolve){
   var recovered=jdbc.queryForList("select id from trailers where tenant_id=? and id=? and active and location_state='AT_LOCATION' and current_location_id is not null for share",tenant,before.relatedResource().id());
   if(recovered.isEmpty())throw AlertFailure.state();
   jdbc.update("update alerts set status='RESOLVED',resolved_by=?,resolved_at=clock_timestamp(),resolution_reason=?,version=version+1 where tenant_id=? and id=?",actor.userId(),reason,tenant,id);
  }else{
   jdbc.update("update alerts set status='ACKNOWLEDGED',acknowledged_by=?,acknowledged_at=clock_timestamp(),version=version+1 where tenant_id=? and id=?",actor.userId(),tenant,id);
   jdbc.update("update alert_recipients set seen_at=coalesce(seen_at,clock_timestamp()) where tenant_id=? and alert_id=? and user_id=?",tenant,id,actor.userId());
  }
  return detail(actor,tenant,id);
 }
 static void keys(Map<?,?> fields,Set<String> allowed){if(!allowed.containsAll(fields.keySet()))throw AlertFailure.invalid();}
 static Instant time(String s){try{return s==null?null:Instant.parse(s);}catch(java.time.DateTimeException e){throw AlertFailure.invalid();}}
 static void scope(StringBuilder sql,List<Object> args,CustomerAccessScope scope){if(!scope.tenantWide()){
  sql.append(" AND t.designated_customer_id IN (").append(String.join(",",Collections.nCopies(scope.customerIds().size(),"?"))).append(")");args.addAll(scope.customerIds());
 }}
 Alert row(ResultSet r,int index)throws SQLException{
  var id=r.getObject("id",UUID.class);var tenant=r.getObject("tenant_id",UUID.class);
  var context=new LinkedHashMap<String,String>();var data=tools.jackson.databind.json.JsonMapper.builder().build().readTree(r.getString("message_data"));
  for(var key:List.of("yardCheckId","itemId","movementEventId"))if(data.path(key).isTextual())context.put(key,data.path(key).asText());
  var diagnostics=new LinkedHashMap<String,Long>();jdbc.query("""
   select diagnostic,count(*) from (
    select case when exists(select 1 from outbox_jobs j where j.tenant_id=r.tenant_id
      and j.job_type='ALERT_FCM' and j.aggregate_id=r.alert_id and j.status='DEAD'
      and j.payload->'data'->>'recipientId'=r.id::text) then 'DEAD' else r.push_status end as diagnostic
    from alert_recipients r where r.tenant_id=? and r.alert_id=?
   ) states group by diagnostic
   """,(org.springframework.jdbc.core.RowCallbackHandler)rs->diagnostics.put(rs.getString(1),rs.getLong(2)),tenant,id);
  return new Alert(id,tenant,r.getString("alert_type"),r.getString("severity"),r.getString("status"),r.getLong("version"),r.getTimestamp("created_at").toInstant(),
   r.getObject("acknowledged_by",UUID.class),instant(r,"acknowledged_at"),r.getObject("resolved_by",UUID.class),instant(r,"resolved_at"),r.getString("resolution_reason"),r.getString("related_entity_type"),
   new Resource(r.getObject("related_entity_id",UUID.class),r.getString("operational_number"),r.getObject("designated_customer_id",UUID.class),r.getString("location_state"),r.getObject("current_location_id",UUID.class)),Map.copyOf(context),Map.copyOf(diagnostics));
 }
 static Instant instant(ResultSet r,String field)throws SQLException{return r.getTimestamp(field)==null?null:r.getTimestamp(field).toInstant();}
}
