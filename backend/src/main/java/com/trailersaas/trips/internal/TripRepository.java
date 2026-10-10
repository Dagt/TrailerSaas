package com.trailersaas.trips.internal;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
class TripRepository {
  private final EntityManager em;

  TripRepository(EntityManager em) {
    this.em = em;
  }

  TripEntity resource(UUID tenant, UUID id, boolean lock) {
    var q =
        em.createQuery(
                "select t from TripEntity t where t.tenantId=:tenant and t.id=:id",
                TripEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("id", id);
    if (lock) {
      q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }
    return q.getResultStream().findFirst().orElseThrow(TripFailure::missing);
  }

  List<TripEntity> list(UUID tenant, TripValidation.Filters f) {
    var q =
        em.createNativeQuery(
                "select t.* from trips t where t.tenant_id=:tenant"
                    + (f.status() == null ? "" : " and t.status=:status")
                    + (f.driverId() == null ? "" : " and t.current_driver_id=:driver")
                    + (f.customerId() == null
                        ? ""
                        : " and exists (select 1 from trip_stops s join trip_stop_orders a on"
                            + " a.tenant_id=s.tenant_id and a.trip_stop_id=s.id join orders o on"
                            + " o.tenant_id=a.tenant_id and o.id=a.order_id where"
                            + " s.tenant_id=t.tenant_id and s.trip_id=t.id and"
                            + " o.customer_id=:customer)")
                    + (f.cursor() == null
                        ? ""
                        : " and (t.created_at<:at or (t.created_at=:at and t.id<:id))")
                    + " order by t.created_at desc,t.id desc",
                TripEntity.class)
            .setParameter("tenant", tenant);
    if (f.status() != null) {
      q.setParameter("status", f.status());
    }
    if (f.driverId() != null) {
      q.setParameter("driver", f.driverId());
    }
    if (f.customerId() != null) {
      q.setParameter("customer", f.customerId());
    }
    if (f.cursor() != null) {
      q.setParameter("at", f.cursor().createdAt());
      q.setParameter("id", f.cursor().id());
    }
    return q.setMaxResults(f.limit() + 1).getResultList();
  }

  List<TripStopEntity> stops(UUID tenant, UUID trip) {
    return em.createQuery(
            "select s from TripStopEntity s where s.tenantId=:tenant and s.tripId=:trip order by"
                + " s.sequenceNumber,s.id",
            TripStopEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .getResultList();
  }

  List<TripStopEntity> stopsForTrips(UUID tenant,List<UUID> trips) {
    return em.createQuery("select s from TripStopEntity s where s.tenantId=:tenant and s.tripId in :trips order by s.sequenceNumber,s.id",TripStopEntity.class).setParameter("tenant",tenant).setParameter("trips",trips).getResultList();
  }

  List<TripEntity> assignedWork(UUID tenant, UUID driver, TripValidation.Position cursor, int limit) {
    var q=em.createQuery("select t from TripEntity t where t.tenantId=:tenant and t.currentDriverId=:driver and t.status not in ('COMPLETED','CANCELLED')"
        +(cursor==null?"":" and (t.createdAt<:at or (t.createdAt=:at and t.id<:id))")+" order by t.createdAt desc,t.id desc",TripEntity.class)
        .setParameter("tenant",tenant).setParameter("driver",driver);
    if(cursor!=null) q.setParameter("at",cursor.createdAt()).setParameter("id",cursor.id());
    return q.setMaxResults(limit+1).getResultList();
  }

  List<TripStopOrderEntity> mappingsForTrips(UUID tenant,List<UUID> trips) {
    return em.createQuery("select a from TripStopOrderEntity a where a.tenantId=:tenant and a.tripStopId in (select s.id from TripStopEntity s where s.tenantId=:tenant and s.tripId in :trips) order by a.orderId,a.id",TripStopOrderEntity.class).setParameter("tenant",tenant).setParameter("trips",trips).getResultList();
  }

  List<TripStopOrderEntity> mappings(UUID tenant, UUID trip) {
    return em.createQuery(
            "select a from TripStopOrderEntity a where a.tenantId=:tenant and a.tripStopId in"
                + " (select s.id from TripStopEntity s where s.tenantId=:tenant and s.tripId=:trip)"
                + " order by a.orderId,a.id",
            TripStopOrderEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .getResultList();
  }

  List<UUID> tripsForOrder(UUID tenant, UUID order) {
    return em.createQuery(
            "select distinct s.tripId from TripStopEntity s,TripStopOrderEntity a where"
                + " s.tenantId=:tenant and a.tenantId=:tenant and a.tripStopId=s.id and"
                + " a.orderId=:order order by s.tripId",
            UUID.class)
        .setParameter("tenant", tenant)
        .setParameter("order", order)
        .getResultList();
  }

  void releaseMappings(UUID tenant, UUID trip, UUID order) {
    em.createQuery(
            "delete from TripStopOrderEntity a where a.tenantId=:tenant and a.orderId=:order and"
                + " a.tripStopId in (select s.id from TripStopEntity s where s.tenantId=:tenant and"
                + " s.tripId=:trip)")
        .setParameter("tenant", tenant)
        .setParameter("order", order)
        .setParameter("trip", trip)
        .executeUpdate();
  }

  Set<UUID> mappedCustomers(UUID tenant, UUID trip) {
    // Bounded by the locked Trip parent; cancellation release takes that same parent lock.
    // No Order locks here: cancellation orders its locks Order -> Trip.
    return new HashSet<UUID>(
        em.createNativeQuery(
                "select distinct o.customer_id from trip_stops s join trip_stop_orders a on"
                    + " a.tenant_id=s.tenant_id and a.trip_stop_id=s.id join orders o on"
                    + " o.tenant_id=a.tenant_id and o.id=a.order_id where s.tenant_id=:tenant and"
                    + " s.trip_id=:trip",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("trip", trip)
            .getResultList());
  }

  List<TripAssignmentEventEntity> history(UUID tenant, UUID trip) {
    return em.createQuery(
            "select e from TripAssignmentEventEntity e where e.tenantId=:tenant and e.tripId=:trip"
                + " order by e.occurredAt,e.id",
            TripAssignmentEventEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .getResultList();
  }

  TripStopEntity lockStop(UUID tenant, UUID trip, UUID stop) {
    return em.createQuery(
            "select s from TripStopEntity s where s.tenantId=:tenant and s.tripId=:trip and"
                + " s.id=:id",
            TripStopEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .setParameter("id", stop)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst()
        .orElseThrow(TripFailure::missing);
  }

  List<TripStopEntity> lockedStops(UUID tenant, UUID trip) {
    return em.createQuery(
            "select s from TripStopEntity s where s.tenantId=:tenant and s.tripId=:trip order by"
                + " s.sequenceNumber,s.id",
            TripStopEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();
  }

  Set<UUID> stopCustomers(UUID tenant, UUID trip, UUID stop) {
    return new HashSet<UUID>(
        em.createNativeQuery(
                "select distinct o.customer_id from trip_stops s join trip_stop_orders a on"
                    + " a.tenant_id=s.tenant_id and a.trip_stop_id=s.id join orders o on"
                    + " o.tenant_id=a.tenant_id and o.id=a.order_id where s.tenant_id=:tenant and"
                    + " s.trip_id=:trip and s.id=:stop",
                UUID.class)
            .setParameter("tenant", tenant)
            .setParameter("trip", trip)
            .setParameter("stop", stop)
            .getResultList());
  }

  List<TripOperationalEventEntity> operationalHistory(UUID tenant, UUID trip) {
    return em.createQuery(
            "select e from TripOperationalEventEntity e where e.tenantId=:tenant and e.tripId=:trip"
                + " order by e.occurredAt,e.id",
            TripOperationalEventEntity.class)
        .setParameter("tenant", tenant)
        .setParameter("trip", trip)
        .getResultList();
  }

  List<TripDriverUpdateEntity> driverUpdates(
      UUID tenant, UUID driver, TripValidation.Position cursor, int limit) {
    var q =
        em.createQuery(
                "select u from TripDriverUpdateEntity u where u.tenantId=:tenant and"
                    + " u.driverId=:driver"
                    + (cursor == null
                        ? ""
                        : " and (u.createdAt>:at or (u.createdAt=:at and u.id>:id))")
                    + " order by u.createdAt,u.id",
                TripDriverUpdateEntity.class)
            .setParameter("tenant", tenant)
            .setParameter("driver", driver);
    if (cursor != null) {
      q.setParameter("at", cursor.createdAt());
      q.setParameter("id", cursor.id());
    }
    return q.setMaxResults(limit + 1).getResultList();
  }

  void requirePinnedTenant(UUID tenant) {
    if (!Boolean.TRUE.equals(
        em.createNativeQuery(
                "select"
                    + " coalesce(NULLIF(current_setting('app.current_tenant_id',true),'')::uuid=:tenant"
                    + " and NULLIF(current_setting('app.current_user_id',true),'') is null,false)",
                Boolean.class)
            .setParameter("tenant", tenant)
            .getSingleResult())) throw TripFailure.missing();
  }

  void insert(Object row) {
    em.persist(row);
  }

  void flush() {
    em.flush();
  }
}
