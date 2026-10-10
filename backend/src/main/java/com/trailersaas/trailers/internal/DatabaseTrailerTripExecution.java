package com.trailersaas.trailers.internal;

import com.trailersaas.trailers.*;
import com.trailersaas.tractors.TractorCouplingPolicy;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
class DatabaseTrailerTripExecution implements TrailerTripExecution {
  private final EntityManager em;
  private final TractorCouplingPolicy tractors;
  private final TrailerMovementProjection movements;
  DatabaseTrailerTripExecution(EntityManager em, TractorCouplingPolicy tractors, TrailerMovementProjection movements) {
    this.em=em; this.tractors=tractors; this.movements=movements;
  }
  public Coupled current(UUID tenant, UUID trip) {
    var c=coupling(tenant,trip);
    if(c==null) return null;
    var t=trailer(tenant,c.trailerId,false);
    return new Coupled(c.id,c.trailerId,t.operationalNumber,c.tractorId,c.driverId,c.hookedAt,t.version,t.currentLocationId);
  }
  private TrailerCouplingEntity coupling(UUID tenant, UUID trip) {
    var rows=em.createQuery("select c from TrailerCouplingEntity c where c.tenantId=:tenant and c.tripId=:trip and c.droppedAt is null",TrailerCouplingEntity.class).setParameter("tenant",tenant).setParameter("trip",trip).getResultList();
    if(rows.size()>1) throw new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.CONFLICT);
    if(rows.isEmpty()) return null;
    return rows.getFirst();
  }
  private TrailerEntity locked(UUID tenant,UUID trip,UUID driver,UUID tractor,boolean active) {
    if(tractor==null || !tractors.lock(tenant,tractor,active)) throw new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.CONFLICT);
    var c=coupling(tenant,trip);
    if(c==null) {
      if(!em.createQuery("select c.id from TrailerCouplingEntity c where c.tenantId=:tenant and c.tractorId=:tractor and c.droppedAt is null",UUID.class).setParameter("tenant",tenant).setParameter("tractor",tractor).getResultList().isEmpty()) throw new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.CONFLICT);
      return null;
    }
    if(!tractor.equals(c.tractorId)) throw new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.CONFLICT);
    // Reassignment does not rewrite the historical Hook Driver. New movement records actual actor.
    return trailer(tenant,c.trailerId,true);
  }
  public void depart(UUID tenant,UUID trip,UUID driver,UUID tractor,UUID expected,TrailerMovementProjection.Context ctx) {
    var t=locked(tenant,trip,driver,tractor,true);
    if(t==null) { if(expected!=null) throw new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.CONFLICT); return; }
    if(!t.active || !"IN_SERVICE".equals(t.serviceabilityState) || !t.id.equals(expected)) throw new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.CONFLICT);
    movements.depart(tenant,new TrailerMovementProjection.Departure(new TrailerMovementProjection.Target(t.id,t.version),ctx));
  }
  public void arrive(UUID tenant,UUID trip,UUID driver,UUID tractor,UUID location,TrailerMovementProjection.Context ctx) {
    var t=locked(tenant,trip,driver,tractor,false);
    if(t!=null && location!=null) movements.arrive(tenant,new TrailerMovementProjection.Arrival(new TrailerMovementProjection.Target(t.id,t.version),ctx,location,TrailerMovementProjection.ArrivalSource.MANUAL));
  }
  public boolean serviceSatisfied(UUID tenant,UUID trip,UUID stop,String type,Instant at) {
    if(!Set.of("DROP_HOOK","LIVE_LOAD","LIVE_UNLOAD").contains(type)) return true;
    var c=current(tenant,trip);
    if(c==null) return false;
    if(!type.equals("DROP_HOOK")) return !c.hookedAt().isAfter(at);
    return Boolean.TRUE.equals(em.createNativeQuery("select exists(select 1 from trailer_couplings old join trailer_movement_events e on e.tenant_id=old.tenant_id and e.id=old.drop_event_id where old.tenant_id=:tenant and old.trip_id=:trip and e.trip_stop_id=:stop and old.dropped_at>=:at and old.dropped_at<=:hooked and old.trailer_id<>:replacement)",Boolean.class).setParameter("tenant",tenant).setParameter("trip",trip).setParameter("stop",stop).setParameter("at",at).setParameter("hooked",c.hookedAt()).setParameter("replacement",c.trailerId()).getSingleResult());
  }
  private TrailerEntity trailer(UUID tenant,UUID id,boolean lock) {
    var q=em.createQuery("select t from TrailerEntity t where t.tenantId=:tenant and t.id=:id",TrailerEntity.class).setParameter("tenant",tenant).setParameter("id",id);
    if(lock) q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    return q.getResultStream().findFirst().orElseThrow(()->new CouplingCommandRejectedException(CouplingCommandRejectedException.Reason.MISSING));
  }
}
