package com.trailersaas.trips.internal;

import com.trailersaas.identity.*;
import com.trailersaas.orders.CustomerOrderProgress;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component @Transactional(propagation=Propagation.MANDATORY,readOnly=true)
class DatabaseCustomerOrderProgress implements CustomerOrderProgress {
    private final EntityManager em;
    private final TripRepository repository;
    private final CustomerActionAccess actions;
    private final TrailerPlanningPolicy trailers;
    DatabaseCustomerOrderProgress(EntityManager em,TripRepository repository,CustomerActionAccess actions,TrailerPlanningPolicy trailers) {
        this.em=em;this.repository=repository;this.actions=actions;this.trailers=trailers;
    }
    public Page read(AuthenticatedUserPrincipal actor,UUID tenant,UUID order,UUID customer,UUID after,int limit) {
        var capabilities=actions.capabilities(actor,tenant,customer);
        if(!capabilities.contains(Capability.ORDER_VIEW_CUSTOMER_SCOPED) && !capabilities.contains(Capability.ORDER_VIEW_ALL)) throw TripFailure.missing();
        if(((Number)em.createNativeQuery("select count(*) from orders where tenant_id=:tenant and id=:order and customer_id=:customer")
                .setParameter("tenant",tenant).setParameter("order",order).setParameter("customer",customer).getSingleResult()).longValue()!=1) throw TripFailure.missing();
        @SuppressWarnings("unchecked")
        List<UUID> ids=after!=null?continuation(tenant,order,after,limit):em.createNativeQuery("select distinct s.trip_id from trip_stops s join trip_stop_orders a on a.tenant_id=s.tenant_id and a.trip_stop_id=s.id where a.tenant_id=:tenant and a.order_id=:order order by s.trip_id",UUID.class)
            .setParameter("tenant",tenant).setParameter("order",order).setMaxResults(limit+1).getResultList();
        boolean more=ids.size()>limit;
        var page=more?ids.subList(0,limit):ids;
        var result=new ArrayList<Trip>();
        for(var id:page) {
            var t=repository.resource(tenant,id,false);
            var relevant=repository.orderProgressStops(tenant,id,order);
            var mapped=relevant.stream().map(s->s.id).collect(Collectors.toSet());
            var customers=repository.mappedCustomers(tenant,id);
            // No broad Driver/Tractor/history/other-Order projection, even with multi-Customer access.
            boolean reroute=actions.covers(actor,tenant,customers,Capability.TRIP_REROUTE);
            boolean substitute=actions.covers(actor,tenant,customers,Capability.TRAILER_SUBSTITUTE_AUTHORIZE);
            Trailer expected=null;
            if(t.plannedInitialTrailerId!=null && trailers.visible(actor,tenant,t.plannedInitialTrailerId)==TrailerPlanningPolicy.Eligibility.ELIGIBLE) {
                var number=em.createNativeQuery("select operational_number from trailers where tenant_id=:tenant and id=:id",String.class).setParameter("tenant",tenant).setParameter("id",t.plannedInitialTrailerId).getSingleResult();
                expected=new Trailer(t.plannedInitialTrailerId,(String)number);
            }
            boolean active=Set.of("IN_TRANSIT","AT_STOP").contains(t.status);
            var stops=relevant.stream().map(s->new Stop(s.id,s.stopType,s.status,s.siteId,s.locationId,
                    s.siteSnapshot==null?null:Objects.toString(s.siteSnapshot.get("name"),null),s.scheduledAt,s.arrivedAt,s.completedAt,
                    reroute&&active&&Set.of("PENDING","EN_ROUTE").contains(s.status))).toList();
            var history=new ArrayList<Change>();
            // Shared Stop instructions/reasons may describe other Customers. Never expose them.
            if(customers.equals(Set.of(customer))) {
                for(var e:repository.orderReroutes(tenant,id,mapped))
                    history.add(new Change(e.id,e.eventType,e.occurredAt,e.reason,destination(e.metadata,"previous"),destination(e.metadata,"replacement"),null));
                for(var e:repository.orderSubstitutions(tenant,id))
                    history.add(new Change(e.id,e.eventType,e.occurredAt,e.reason,null,null,e.eventType.endsWith("AUTHORIZED")?"AUTHORIZE":"REJECT"));
            }
            history.sort(Comparator.comparing(Change::occurredAt).thenComparing(Change::id));
            result.add(new Trip(t.id,t.status,t.version,expected,substitute&&expected!=null&&Set.of("ASSIGNED","IN_TRANSIT","AT_STOP").contains(t.status),stops,history));
        }
        return new Page(List.copyOf(result),more?page.getLast().toString():null);
    }
    @SuppressWarnings("unchecked") private List<UUID> continuation(UUID tenant,UUID order,UUID after,int limit) {
        return em.createNativeQuery("select distinct s.trip_id from trip_stops s join trip_stop_orders a on a.tenant_id=s.tenant_id and a.trip_stop_id=s.id where a.tenant_id=:tenant and a.order_id=:order and s.trip_id>:after order by s.trip_id",UUID.class)
                .setParameter("tenant",tenant).setParameter("order",order).setParameter("after",after).setMaxResults(limit+1).getResultList();
    }
    private static String destination(Map<String,Object> metadata,String key) {
        if(metadata!=null && metadata.get(key) instanceof Map<?,?> m && m.get("siteSnapshot") instanceof Map<?,?> snapshot) return Objects.toString(snapshot.get("name"),null);
        return null;
    }
}
