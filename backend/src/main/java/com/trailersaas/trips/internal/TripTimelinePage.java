package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripContracts.*;

import com.trailersaas.ReadCursor;
import jakarta.persistence.EntityManager;
import java.util.*;

/** SQL-bounded union; at most two batch entity reads, never an in-memory slice of full history. */
final class TripTimelinePage {
  private static final UUID ASSIGNMENT = new UUID(0, 1);
  private static final UUID OPERATIONAL = new UUID(0, 2);

  static TimelinePage read(EntityManager em, UUID user, UUID tenant, UUID trip, Map<String, String> query) {
    TripValidation.fields(query, Set.of("limit", "cursor"));
    int limit = 50;
    if (query.containsKey("limit")) {
      try { limit = Integer.parseInt(query.get("limit")); }
      catch (NumberFormatException e) { throw TripFailure.invalid("limit"); }
    }
    if (limit < 1 || limit > 100) throw TripFailure.invalid("limit");
    String context = user + "|" + tenant + "|" + trip + "|trip-timeline-ascending";
    List<UUID> anchor;
    try { anchor = ReadCursor.decode(query.get("cursor"), context, 2); }
    catch (IllegalArgumentException e) { throw TripFailure.invalid("cursor"); }
    HistoryItem previous = null;
    int previousKind = 0;
    if (!anchor.isEmpty()) {
      previousKind = anchor.get(1).equals(ASSIGNMENT) ? 0 : anchor.get(1).equals(OPERATIONAL) ? 1 : -1;
      if (previousKind < 0) throw TripFailure.invalid("cursor");
      var rows = events(em, tenant, trip, List.of(anchor.getFirst()), previousKind);
      if (rows.isEmpty()) throw TripFailure.invalid("cursor");
      previous = rows.getFirst();
    }
    String seek = previous == null ? "" : " and (occurred_at,id,%d)>(:at,:id,:kind)";
    var sql = em.createNativeQuery("select id,kind from ((select id,occurred_at,0 as kind from trip_assignment_events where tenant_id=:tenant and trip_id=:trip"
        + seek.formatted(0) + " order by occurred_at,id limit :bound) union all (select id,occurred_at,1 as kind from trip_operational_events where tenant_id=:tenant and trip_id=:trip"
        + seek.formatted(1) + " order by occurred_at,id limit :bound)) history order by occurred_at,id,kind limit :bound");
    sql.setParameter("tenant", tenant).setParameter("trip", trip).setParameter("bound", limit + 1);
    if (previous != null) sql.setParameter("at", previous.occurredAt()).setParameter("id", previous.id()).setParameter("kind", previousKind);
    @SuppressWarnings("unchecked") List<Object[]> positions = sql.getResultList();
    boolean more = positions.size() > limit;
    var page = more ? positions.subList(0, limit) : positions;
    var byKey = new HashMap<String, HistoryItem>();
    for (int kind = 0; kind < 2; kind++) {
      int k = kind;
      var ids = page.stream().filter(p -> ((Number) p[1]).intValue() == k).map(p -> (UUID) p[0]).toList();
      events(em, tenant, trip, ids, kind).forEach(e -> byKey.put(k + ":" + e.id(), e));
    }
    var items = page.stream().map(p -> byKey.get(((Number) p[1]).intValue() + ":" + p[0])).toList();
    var last = page.isEmpty() ? null : page.getLast();
    return new TimelinePage(items, more ? ReadCursor.encode(context, (UUID) last[0], ((Number) last[1]).intValue() == 0 ? ASSIGNMENT : OPERATIONAL) : null);
  }

  private static List<HistoryItem> events(EntityManager em, UUID tenant, UUID trip, List<UUID> ids, int kind) {
    if (ids.isEmpty()) return List.of();
    if (kind == 0) return em.createQuery("select e from TripAssignmentEventEntity e where e.tenantId=:tenant and e.tripId=:trip and e.id in :ids", TripAssignmentEventEntity.class)
        .setParameter("tenant", tenant).setParameter("trip", trip).setParameter("ids", ids).getResultList().stream().<HistoryItem>map(Assignment::of).toList();
    return em.createQuery("select e from TripOperationalEventEntity e where e.tenantId=:tenant and e.tripId=:trip and e.id in :ids", TripOperationalEventEntity.class)
        .setParameter("tenant", tenant).setParameter("trip", trip).setParameter("ids", ids).getResultList().stream().<HistoryItem>map(Operational::of).toList();
  }
}
