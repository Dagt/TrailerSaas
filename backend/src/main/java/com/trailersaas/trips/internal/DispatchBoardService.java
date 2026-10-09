package com.trailersaas.trips.internal;

import com.trailersaas.identity.*;
import com.trailersaas.orders.OrderDispatchDirectory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DispatchBoardService {
  record NextStop(
      UUID id,
      int sequenceNumber,
      String stopType,
      UUID siteId,
      UUID locationId,
      String snapshotName,
      Instant scheduledAt,
      String status) {}

  record ActiveTrip(
      UUID id,
      String status,
      UUID currentDriverId,
      UUID currentTractorId,
      UUID plannedInitialTrailerId,
      long version,
      Instant createdAt,
      Instant updatedAt,
      NextStop nextPlannedStop) {}

  record Attention(String type, UUID tripId) {}

  record Board(
      Instant generatedAt,
      List<OrderDispatchDirectory.Pending> pendingOrders,
      List<ActiveTrip> activeTrips,
      List<Attention> attentionItems) {}

  private final TenantAuthorization authorization;
  private final OrderDispatchDirectory orders;
  private final EntityManager em;

  DispatchBoardService(
      TenantAuthorization authorization, OrderDispatchDirectory orders, EntityManager em) {
    this.authorization = authorization;
    this.orders = orders;
    this.em = em;
  }

  Board get(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, String> parameters) {
    authorization.requireTenantCapability(actor, tenant, Capability.TRIP_VIEW_ALL);
    authorization.requireTenantCapability(actor, tenant, Capability.ORDER_VIEW_ALL);
    TripValidation.fields(parameters, Set.of("customerId", "driverId", "status"));
    var f = TripValidation.filters(parameters);
    // One bounded current-projection query, including at most one executable Stop per Trip.
    // No event tables or per-Trip history queries; Tenant predicates supplement normal runtime RLS.
    var q =
        em.createNativeQuery(
                """
                select t.id,t.status,t.current_driver_id,t.current_tractor_id,t.planned_initial_trailer_id,
                  t.version,t.created_at,t.updated_at,s.id,s.sequence_number,s.stop_type,s.site_id,s.location_id,
                  s.site_snapshot->>'name',s.scheduled_at,s.status
                from trips t left join lateral (
                  select s.* from trip_stops s where s.tenant_id=t.tenant_id and s.trip_id=t.id
                  and s.status not in ('COMPLETED','CANCELLED') order by s.sequence_number,s.id limit 1
                ) s on true
                where t.tenant_id=:tenant and t.status in ('PLANNED','ASSIGNED','IN_TRANSIT','AT_STOP','CANCELLING')
                """
                    + (f.status() == null ? "" : " and t.status=:status")
                    + (f.driverId() == null ? "" : " and t.current_driver_id=:driver")
                    + (f.customerId() == null
                        ? ""
                        : " and exists(select 1 from trip_stops x join trip_stop_orders a on"
                              + " a.tenant_id=x.tenant_id and a.trip_stop_id=x.id join orders o on"
                              + " o.tenant_id=a.tenant_id and o.id=a.order_id where"
                              + " x.tenant_id=t.tenant_id and x.trip_id=t.id and"
                              + " o.customer_id=:customer)")
                    + " order by t.created_at desc,t.id desc")
            .setParameter("tenant", tenant)
            .setMaxResults(20);
    if (f.status() != null) q.setParameter("status", f.status());
    if (f.driverId() != null) q.setParameter("driver", f.driverId());
    if (f.customerId() != null) q.setParameter("customer", f.customerId());
    var rows = q.getResultList();
    var trips = new ArrayList<ActiveTrip>();
    for (Object row : rows) {
      var r = (Object[]) row;
      var stop =
          r[8] == null
              ? null
              : new NextStop(
                  (UUID) r[8],
                  ((Number) r[9]).intValue(),
                  (String) r[10],
                  (UUID) r[11],
                  (UUID) r[12],
                  (String) r[13],
                  instant(r[14]),
                  (String) r[15]);
      trips.add(
          new ActiveTrip(
              (UUID) r[0],
              (String) r[1],
              (UUID) r[2],
              (UUID) r[3],
              (UUID) r[4],
              ((Number) r[5]).longValue(),
              instant(r[6]),
              instant(r[7]),
              stop));
    }
    var attention =
        trips.stream()
            .filter(
                t ->
                    t.status().equals("CANCELLING")
                        || t.status().equals("PLANNED")
                            && (t.currentDriverId() == null || t.currentTractorId() == null))
            .map(
                t ->
                    new Attention(
                        t.status().equals("CANCELLING") ? "CANCELLING" : "INCOMPLETE_ASSIGNMENT",
                        t.id()))
            .toList();
    return new Board(
        Instant.now(),
        orders.pending(actor, tenant, f.customerId()),
        List.copyOf(trips),
        attention);
  }

  private static Instant instant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant i) return i;
    if (value instanceof java.time.OffsetDateTime d) return d.toInstant();
    return ((java.sql.Timestamp) value).toInstant();
  }
}
