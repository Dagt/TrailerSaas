package com.trailersaas.orders.internal;

import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import com.trailersaas.orders.OrderLifecycleEvent.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
class DatabaseOrderFulfillment implements OrderFulfillment {
  private final JdbcTemplate jdbc;
  private final OrderRepository repository;
  private final OrderMutationService mutations;
  DatabaseOrderFulfillment(JdbcTemplate j,OrderRepository r,OrderMutationService m){jdbc=j;repository=r;mutations=m;}
  private void pinned(UUID tenant) {
    if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT coalesce(NULLIF(current_setting('app.current_tenant_id',true),'')::uuid=? AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL,false)",Boolean.class,tenant)))throw reject(OrderPlanningRejectedException.Reason.MISSING);
  }
  public List<UUID> lockTripOrders(UUID tenant,UUID trip) {
    pinned(tenant);
    var ids=jdbc.query("SELECT DISTINCT a.order_id FROM trip_stop_orders a JOIN trip_stops s ON s.tenant_id=a.tenant_id AND s.id=a.trip_stop_id WHERE s.tenant_id=? AND s.trip_id=? ORDER BY a.order_id LIMIT 201",(r,n)->r.getObject(1,UUID.class),tenant,trip);
    if(ids.size()>200)throw reject(OrderPlanningRejectedException.Reason.CONFLICT);
    for(var id:ids)repository.lockForMutation(tenant,id,new CustomerAccessScope(true,Set.of()));
    return List.copyOf(ids);
  }
  public List<UUID> lockDeliveryOrders(UUID tenant,UUID stop) {
    pinned(tenant);
    var parent=jdbc.query("SELECT trip_id FROM trip_stops WHERE tenant_id=? AND id=?",(r,n)->r.getObject(1,UUID.class),tenant,stop).stream().findFirst().orElseThrow(()->reject(OrderPlanningRejectedException.Reason.MISSING));
    return lockTripOrders(tenant,parent);
  }
  public void started(UUID tenant,List<UUID> ids) {
    pinned(tenant);
    for(var id:ids) {
      var order=own(tenant,id);
      // A persisted operational allocation is already planning, even for legacy OPEN projections.
      if(order.status==OrderStatus.OPEN)mutations.transition(order,Action.ORDER_PLANNED,Actor.system(),null,null);
      if(order.status==OrderStatus.PLANNED)mutations.transition(order,Action.ORDER_FULFILLMENT_STARTED,Actor.system(),null,null);
      else if(!Set.of(OrderStatus.IN_PROGRESS,OrderStatus.PARTIALLY_DELIVERED).contains(order.status))throw reject(OrderPlanningRejectedException.Reason.STATE);
    }
  }
  private OrderEntity own(UUID tenant,UUID id) {return repository.lockForMutation(tenant,id,new CustomerAccessScope(true,Set.of()));}
  record DeliveryStop(UUID id,UUID requested,UUID site,UUID location,String snapshot,boolean successful) {}
  public void delivered(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop,UUID operation,Instant at,List<UUID> locked) {
    pinned(tenant);
    var affected=jdbc.query("SELECT DISTINCT order_id FROM trip_stop_orders WHERE tenant_id=? AND trip_stop_id=? ORDER BY order_id",(r,n)->r.getObject(1,UUID.class),tenant,stop);
    if(!locked.containsAll(affected))throw reject(OrderPlanningRejectedException.Reason.CONFLICT);
    for(var id:affected) {
      var order=own(tenant,id);
      if(!Set.of(OrderStatus.PLANNED,OrderStatus.IN_PROGRESS,OrderStatus.PARTIALLY_DELIVERED).contains(order.status))throw reject(OrderPlanningRejectedException.Reason.STATE);
      var previous=order.status;
      var visits=jdbc.query("""
        SELECT s.id,a.order_stop_id,s.site_id,s.location_id,s.site_snapshot::text,
          (s.status='COMPLETED' AND (EXISTS(SELECT 1 FROM delivery_evidence e WHERE e.tenant_id=s.tenant_id AND e.trip_stop_id=s.id)
            OR (s.id=? AND s.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296)
              AND EXISTS(SELECT 1 FROM trip_stop_events e WHERE e.tenant_id=s.tenant_id AND e.trip_stop_id=s.id AND e.event_type='COMPLETED'
                AND e.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296))))) successful
        FROM trip_stop_orders a JOIN trip_stops s ON s.tenant_id=a.tenant_id AND s.id=a.trip_stop_id
        WHERE a.tenant_id=? AND a.order_id=? AND s.stop_type='DELIVERY' ORDER BY s.id,a.id LIMIT 501
        """,(r,n)->new DeliveryStop(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getString(5),r.getBoolean(6)),stop,tenant,id);
      if(visits.isEmpty() || visits.size()>500 || visits.stream().noneMatch(v->v.id().equals(stop)&&v.successful()))throw reject(OrderPlanningRejectedException.Reason.CONFLICT);
      var requested=repository.stops(tenant,id).stream().filter(s->"DELIVERY".equals(s.stopType)).toList();
      boolean all=visits.stream().allMatch(DeliveryStop::successful) && obligationsSatisfied(requested,visits);
      if(order.status==OrderStatus.PLANNED)mutations.transition(order,Action.ORDER_FULFILLMENT_STARTED,Actor.system(),null,null);
      if(all)mutations.transition(order,Action.ORDER_DELIVERED,Actor.system(),null,null);
      else if(order.status==OrderStatus.IN_PROGRESS)mutations.transition(order,Action.ORDER_PARTIALLY_DELIVERED,Actor.system(),null,null);
      jdbc.update("INSERT INTO order_fulfillment_events VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,id,stop,previous.name(),order.status.name(),actor.userId(),operation,Timestamp.from(at),Timestamp.from(Instant.now()));
    }
  }
  /** One physical visit cannot satisfy two separate requested visits at the same destination. */
  private boolean obligationsSatisfied(List<OrderStopEntity> required,List<DeliveryStop> visits) {
    if(required.isEmpty())return true; // Nonempty actual DELIVERY mappings were checked above.
    var assigned=new HashMap<UUID,UUID>();
    for(var requested:required)if(!match(requested,required,visits,assigned,new HashSet<>()))return false;
    return true;
  }
  private boolean match(OrderStopEntity required,List<OrderStopEntity> all,List<DeliveryStop> visits,Map<UUID,UUID> assigned,Set<UUID> seen) {
    for(var visit:visits) {
      boolean target=visit.requested()!=null?visit.requested().equals(required.id):Objects.equals(visit.site(),required.siteId)
          && Objects.equals(visit.location(),required.locationId) && (required.siteId!=null || Objects.equals(visit.snapshot(),canonicalSnapshot(required.siteSnapshot)));
      if(!visit.successful() || !target || !seen.add(visit.id()))continue;
      UUID old=assigned.get(visit.id());
      if(old==null || match(all.stream().filter(s->s.id.equals(old)).findFirst().orElseThrow(),all,visits,assigned,seen)) {
        assigned.put(visit.id(),required.id);return true;
      }
    }
    return false;
  }
  private String canonicalSnapshot(Map<String,Object> value) {
    // PostgreSQL's jsonb text representation, rather than Java map iteration order.
    return value==null?null:jdbc.queryForObject("SELECT cast(cast(? as jsonb) as text)",String.class,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value));
  }
  private static OrderPlanningRejectedException reject(OrderPlanningRejectedException.Reason reason){return new OrderPlanningRejectedException(reason);}
}
