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
        data.put("cargoLines",repository.cargo(order.tenantId,order.id).stream().map(c->{
            var m=new LinkedHashMap<String,Object>(); m.put("description",c.description);m.put("quantity",c.quantity);m.put("palletCount",c.palletCount);m.put("weight",c.weight);m.put("weightUnit",c.weightUnit);return m;
        }).toList());
        data.put("requestedStops",repository.stops(order.tenantId,order.id).stream().map(s->{
            var m=new LinkedHashMap<String,Object>();m.put("sequenceNumber",s.sequenceNumber);m.put("siteId",s.siteId==null?null:s.siteId.toString());m.put("locationId",s.locationId==null?null:s.locationId.toString());m.put("siteSnapshot",s.siteSnapshot);m.put("stopType",s.stopType);m.put("scheduledAt",s.scheduledAt==null?null:s.scheduledAt.toString());m.put("instructions",s.instructions);return m;
        }).toList());
        return data;
    }
    void replace(OrderEntity order,Map<String,Object> input,UUID actor) {
        if(!editable(order)) throw OrderFailure.transition();
        var previous=snapshot(order);
        var request=new LinkedHashMap<String,Object>(previous);
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
        var revision=new OrderRequestRevisionEntity();revision.id=UUID.randomUUID();revision.tenantId=order.tenantId;revision.orderId=order.id;revision.actorUserId=actor;revision.orderVersion=order.version+1;revision.recordedAt=now;revision.previous=previous;revision.replacement=snapshot(order);repository.insert(revision);
    }
}
