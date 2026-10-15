package com.trailersaas.orders.internal;

import com.trailersaas.customers.OrderDestinationPolicy;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Called only after the Order write lock and optimistic check. No Trip/Manifest rewrite. */
@Component @Transactional(propagation=Propagation.MANDATORY)
class OrderRequestEditor {
    private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    private final EntityManager em;
    private final OrderRepository repository;
    private final OrderDestinationPolicy destinations;
    OrderRequestEditor(EntityManager em,OrderRepository repository,OrderDestinationPolicy destinations) {
        this.em=em;this.repository=repository;this.destinations=destinations;
    }
    boolean editable(OrderEntity order) {
        return order.status==OrderStatus.OPEN && ((Number)em.createNativeQuery("select (select count(*) from manifest_orders where tenant_id=:tenant and order_id=:order)+(select count(*) from trip_stop_orders where tenant_id=:tenant and order_id=:order)+(select count(*) from order_fulfillment_events where tenant_id=:tenant and order_id=:order)")
                .setParameter("tenant",order.tenantId).setParameter("order",order.id).getSingleResult()).longValue()==0;
    }
    Map<String,Object> snapshot(OrderEntity order) {
        var data=new LinkedHashMap<String,Object>();
        data.put("status",order.status.name());data.put("orderNumber",order.orderNumber);data.put("poNumber",order.poNumber);
        data.put("shipmentNumber",order.shipmentNumber);data.put("bolReference",order.bolReference);
        data.put("cargoLines",repository.cargo(order.tenantId,order.id).stream().map(c->{
            var m=new LinkedHashMap<String,Object>(); m.put("description",c.description);m.put("quantity",c.quantity);m.put("palletCount",c.palletCount);m.put("weight",c.weight);m.put("weightUnit",c.weightUnit);return m;
        }).toList());
        data.put("requestedStops",repository.stops(order.tenantId,order.id).stream().map(s->{
            var m=new LinkedHashMap<String,Object>();m.put("sequenceNumber",s.sequenceNumber);m.put("siteId",s.siteId==null?null:s.siteId.toString());m.put("locationId",s.locationId==null?null:s.locationId.toString());m.put("siteSnapshot",s.siteSnapshot);m.put("stopType",s.stopType);m.put("scheduledAt",s.scheduledAt==null?null:s.scheduledAt.toString());m.put("instructions",s.instructions);return m;
        }).toList());
        return data;
    }
    void replace(OrderEntity order,Map<String,Object> input) {
        if(!editable(order)) throw OrderFailure.transition();
        var previous=snapshot(order);
        var request=new LinkedHashMap<String,Object>();
        request.put("cargoLines",previous.get("cargoLines"));request.put("requestedStops",previous.get("requestedStops"));
        request.put("customerId",order.customerId.toString());request.put("orderNumber",order.orderNumber);
        for(var field:List.of("cargoLines","requestedStops")) if(input.containsKey(field)) request.put(field,input.get(field));
        var validated=OrderValidation.create(request);
        // Lock Sites in deterministic order, after the parent Order, as in planning.
        for(var s:validated.requestedStops().stream().filter(s->s.siteId()!=null).sorted(Comparator.comparing(OrderContracts.StopInput::siteId)).toList())
            if(!destinations.compatible(order.tenantId,order.customerId,s.siteId(),s.locationId())) throw OrderFailure.conflict();
        var now=Instant.now();
        if(input.containsKey("cargoLines")) {
            em.createQuery("delete from OrderCargoEntity c where c.tenantId=:tenant and c.orderId=:order").setParameter("tenant",order.tenantId).setParameter("order",order.id).executeUpdate();
            for(var c:validated.cargoLines()) {
                var row=new OrderCargoEntity();row.id=UUID.randomUUID();row.tenantId=order.tenantId;row.orderId=order.id;row.description=c.description();row.quantity=c.quantity();row.palletCount=c.palletCount();row.weight=c.weight();row.weightUnit=c.weightUnit();row.createdAt=now;repository.insert(row);
            }
        }
        if(input.containsKey("requestedStops")) {
            em.createQuery("delete from OrderStopEntity s where s.tenantId=:tenant and s.orderId=:order").setParameter("tenant",order.tenantId).setParameter("order",order.id).executeUpdate();
            for(var s:validated.requestedStops()) {
                var row=new OrderStopEntity();row.id=UUID.randomUUID();row.tenantId=order.tenantId;row.orderId=order.id;row.sequenceNumber=s.sequenceNumber();row.siteId=s.siteId();row.locationId=s.locationId();row.siteSnapshot=s.siteSnapshot();row.stopType=s.stopType();row.scheduledAt=s.scheduledAt();row.instructions=s.instructions();row.createdAt=row.updatedAt=now;repository.insert(row);
            }
        }
        repository.flush();
    }
    void record(OrderEntity order,UUID actor,String changeType,String reason,com.trailersaas.orders.OrderLifecycleEvent.Resolution resolution,Map<String,Object> previous) {
        var replacement=snapshot(order);
        replacement.put("changeType",changeType);replacement.put("reason",reason);replacement.put("resolution",resolution);
        // Native SQL in this same JPA transaction also lets historical upgrade fixtures
        // boot before V28 exists; no new entity is validated against an older schema.
        em.createNativeQuery("insert into order_request_revisions(id,tenant_id,order_id,actor_user_id,order_version,previous_request,replacement_request,recorded_at) values(:id,:tenant,:order,:actor,:version,cast(:previous as jsonb),cast(:replacement as jsonb),:at)")
            .setParameter("id",UUID.randomUUID()).setParameter("tenant",order.tenantId).setParameter("order",order.id)
            .setParameter("actor",actor).setParameter("version",order.version).setParameter("previous",json.writeValueAsString(previous))
            .setParameter("replacement",json.writeValueAsString(replacement)).setParameter("at",java.sql.Timestamp.from(order.updatedAt)).executeUpdate();
    }
    @SuppressWarnings("unchecked")
    List<OrderContracts.RequestChange> history(OrderEntity order) {
        List<Object[]> rows=em.createNativeQuery("select order_version,recorded_at,replacement_request->>'changeType',replacement_request->>'reason',(replacement_request->'resolution')::text from order_request_revisions where tenant_id=:tenant and order_id=:order order by order_version desc")
            .setParameter("tenant",order.tenantId).setParameter("order",order.id).setMaxResults(20).getResultList();
        return rows.stream().map(row->{
            var at=row[1] instanceof java.time.OffsetDateTime offset?offset.toInstant():row[1] instanceof Instant instant?instant:((java.sql.Timestamp)row[1]).toInstant();
            Map<String,Object> resolution=row[4]==null?null:json.readValue((String)row[4],Map.class);
            return new OrderContracts.RequestChange(((Number)row[0]).longValue(),(String)row[2],at,(String)row[3],resolution);
        }).toList();
    }
}
