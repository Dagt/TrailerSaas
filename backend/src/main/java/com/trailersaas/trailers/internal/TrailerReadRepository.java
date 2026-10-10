package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.internal.TrailerReadDtos.*;

import com.trailersaas.identity.CustomerAccessScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tenant-pinned cross-domain SQL read model. No foreign internal Java API, mutation or history
 * replay.
 */
@Repository
class TrailerReadRepository {
  private final EntityManager em;
  private final JsonMapper json = JsonMapper.builder().build();

  TrailerReadRepository(EntityManager em) {
    this.em = em;
  }

  static final String PROJECTION =
      """
      select t.id,t.operational_number,t.designated_customer_id,t.trailer_type,t.capability_notes,
      t.location_state,cl.id,lk.id,t.last_seen_at,t.last_driver_id,t.load_state,t.serviceability_state,
      t.usage_purpose,t.active,t.version,t.created_at,t.updated_at,c.name,
      cl.id,cl.name,cl.type,cl.site_id,cs.name,lk.id,lk.name,lk.type,lk.site_id,ls.name,ld.name,
      ac.id,ac.driver_id,ad.name,ac.tractor_id,at.operational_number,ac.trip_id,ac.hooked_at,ac.dropped_at,
      lc.id,lc.driver_id,hd.name,lc.tractor_id,ht.operational_number,lc.trip_id,lc.hooked_at,lc.dropped_at
      from trailers t
      left join customers c on c.tenant_id=t.tenant_id and c.id=t.designated_customer_id
      left join operational_locations cl on cl.tenant_id=t.tenant_id and cl.id=t.current_location_id %s
      left join physical_sites cs on cs.tenant_id=cl.tenant_id and cs.id=cl.site_id
      left join operational_locations lk on lk.tenant_id=t.tenant_id and lk.id=t.last_known_location_id %s
      left join physical_sites ls on ls.tenant_id=lk.tenant_id and ls.id=lk.site_id
      left join drivers ld on ld.tenant_id=t.tenant_id and ld.id=t.last_driver_id
      left join trailer_couplings ac on ac.tenant_id=t.tenant_id and ac.trailer_id=t.id and ac.dropped_at is null
      left join drivers ad on ad.tenant_id=ac.tenant_id and ad.id=ac.driver_id
      left join tractors at on at.tenant_id=ac.tenant_id and at.id=ac.tractor_id
      left join lateral (select x.* from trailer_couplings x where x.tenant_id=t.tenant_id and x.trailer_id=t.id
        order by x.hooked_at desc,x.id desc limit 1) lc on true
      left join drivers hd on hd.tenant_id=lc.tenant_id and hd.id=lc.driver_id
      left join tractors ht on ht.tenant_id=lc.tenant_id and ht.id=lc.tractor_id
      where t.tenant_id=:tenant
      """;

  private String projection(CustomerAccessScope scope) {
    return PROJECTION.formatted(
            scope.tenantWide()
                ? ""
                : "and (cl.customer_id is null or cl.customer_id in (:customers))",
            scope.tenantWide()
                ? ""
                : "and (lk.customer_id is null or lk.customer_id in (:customers))")
        + (scope.tenantWide() ? "" : " and t.designated_customer_id in (:customers)");
  }

  private Query scoped(String sql, UUID tenant, CustomerAccessScope scope) {
    var q = em.createNativeQuery(sql).setParameter("tenant", tenant);
    if (!scope.tenantWide()) q.setParameter("customers", scope.customerIds());
    return q;
  }

  Items list(UUID tenant, CustomerAccessScope scope, Map<String, Object> filters) {
    var sql = new StringBuilder(projection(scope));
    var columns =
        Map.of(
            "operationalNumber",
            "t.operational_number_normalized",
            "designatedCustomerId",
            "t.designated_customer_id",
            "locationState",
            "t.location_state",
            "currentLocationId",
            "t.current_location_id",
            "loadState",
            "t.load_state",
            "serviceabilityState",
            "t.serviceability_state",
            "active",
            "t.active");
    filters.keySet().stream()
        .sorted()
        .forEach(
            k -> {
              if (k.equals("coupled"))
                sql.append(
                    (Boolean) filters.get(k) ? " and ac.id is not null" : " and ac.id is null");
              else sql.append(" and ").append(columns.get(k)).append("=:").append(k);
            });
    sql.append(" order by t.operational_number_normalized,t.id");
    var q = scoped(sql.toString(), tenant, scope);
    filters.forEach(
        (k, v) -> {
          if (!k.equals("coupled")) q.setParameter(k, v);
        });
    var items = new ArrayList<Item>();
    for (Object row : q.getResultList()) items.add(read((Object[]) row, scope).item());
    return new Items(List.copyOf(items));
  }

  Detail detail(UUID tenant, UUID id, CustomerAccessScope scope) {
    var rows =
        scoped(projection(scope) + " and t.id=:id", tenant, scope)
            .setParameter("id", id)
            .getResultList();
    if (rows.isEmpty()) throw TrailerFailure.missing();
    var row = rows.getFirst();
    var p = read((Object[]) row, scope);
    var manifests = manifests(tenant, id, scope);
    return new Detail(
        p.item(),
        named(p.item().trailer().designatedCustomerId(), p.item().designatedCustomerName()),
        p.current(),
        p.known(),
        named(p.item().trailer().lastDriverId(), p.item().lastDriverName()),
        numbered(p.item().currentTractorId(), p.item().currentTractorOperationalNumber()),
        numbered(p.item().lastTractorId(), p.item().lastTractorOperationalNumber()),
        p.coupling(),
        p.last(),
        manifests.items(),
        manifests.truncated());
  }

  void requireVisible(UUID tenant, UUID id, CustomerAccessScope scope) {
    var q =
        scoped(
                "select t.id from trailers t where t.tenant_id=:tenant and t.id=:id"
                    + (scope.tenantWide() ? "" : " and t.designated_customer_id in (:customers)"),
                tenant,
                scope)
            .setParameter("id", id);
    if (q.getResultList().isEmpty()) throw TrailerFailure.missing();
  }

  record Projection(
      Item item, Location current, Location known, Coupling coupling, Coupling last) {}

  private Projection read(Object[] row, CustomerAccessScope scope) {
    var r = new Row(row);
    var t =
        new TrailerDtos.Trailer(
            r.uuid(),
            r.text(),
            r.uuid(),
            r.text(),
            r.text(),
            r.text(),
            r.uuid(),
            r.uuid(),
            r.instant(),
            r.uuid(),
            r.text(),
            r.text(),
            r.text(),
            r.bool(),
            r.number(),
            r.instant(),
            r.instant());
    String customer = r.text();
    var current = location(r);
    var known = location(r);
    String driver = r.text();
    var coupling = coupling(r, scope);
    var last = coupling(r, scope);
    return new Projection(
        new Item(
            t,
            customer,
            current == null ? null : current.name(),
            known == null ? null : known.name(),
            coupling == null ? "UNCOUPLED" : "COUPLED",
            coupling == null ? null : coupling.driverId(),
            coupling == null ? null : coupling.driverName(),
            coupling == null ? null : coupling.tractorId(),
            coupling == null ? null : coupling.tractorOperationalNumber(),
            driver,
            last == null ? null : last.tractorId(),
            last == null ? null : last.tractorOperationalNumber()),
        current,
        known,
        coupling,
        last);
  }

  private static Location location(Row r) {
    var value = new Location(r.uuid(), r.text(), r.text(), r.uuid(), r.text());
    return value.id() == null ? null : value;
  }

  private static Coupling coupling(Row r, CustomerAccessScope scope) {
    UUID id = r.uuid(), driver = r.uuid();
    String name = r.text();
    UUID tractor = r.uuid();
    String number = r.text();
    UUID trip = r.uuid();
    Instant hooked = r.instant(), dropped = r.instant();
    return id == null
        ? null
        : new Coupling(
            id, driver, name, tractor, number, scope.tenantWide() ? trip : null, hooked, dropped);
  }

  private static Named named(UUID id, String name) {
    return id == null ? null : new Named(id, name);
  }

  private static Numbered numbered(UUID id, String name) {
    return id == null ? null : new Numbered(id, name);
  }

  record ManifestResult(List<Manifest> items, boolean truncated) {}

  private ManifestResult manifests(UUID tenant, UUID trailer, CustomerAccessScope scope) {
    String visible = scope.tenantWide() ? "" : " and o.customer_id in (:customers)";
    String hasVisibleOrders =
        "exists(select 1 from manifest_orders a join orders o on"
            + " o.tenant_id=a.tenant_id and o.id=a.order_id where"
            + " a.tenant_id=m.tenant_id and a.manifest_id=m.id"
            + visible
            + ")";
    var parents =
        scoped(
                "select m.id,m.status,m.version,m.updated_at,"
                    + hasVisibleOrders
                    + """
                     from load_manifests m
                    where m.tenant_id=:tenant and m.trailer_id=:trailer and m.status='OPEN'
                    """
                    + (scope.tenantWide() ? "" : " and " + hasVisibleOrders)
                    + " order by m.updated_at desc,m.id desc",
                tenant,
                scope)
            .setParameter("trailer", trailer)
            .setMaxResults(21)
            .getResultList();
    if (parents.isEmpty()) return new ManifestResult(List.of(), false);
    boolean truncated = parents.size() > 20;
    var selected = parents.subList(0, Math.min(20, parents.size()));
    var ids = selected.stream().map(p -> (UUID) ((Object[]) p)[0]).toList();
    var children =
        scoped(
                """
                select a.manifest_id,o.id,o.customer_id,c.name,o.order_number,o.status
                from manifest_orders a join orders o on o.tenant_id=a.tenant_id and o.id=a.order_id
                join customers c on c.tenant_id=o.tenant_id and c.id=o.customer_id
                join load_manifests m on m.tenant_id=a.tenant_id and m.id=a.manifest_id
                where a.tenant_id=:tenant and a.manifest_id in (:manifests)
                """
                    + visible
                    + " order by m.updated_at desc,m.id desc,o.id",
                tenant,
                scope)
            .setParameter("manifests", ids)
            .setMaxResults(51)
            .getResultList();
    truncated |= children.size() > 50;
    UUID partiallyShownManifest =
        children.size() > 50 ? (UUID) ((Object[]) children.get(50))[0] : null;
    var orders = new HashMap<UUID, List<PlannedOrder>>();
    for (Object value : children.subList(0, Math.min(50, children.size()))) {
      var r = new Row((Object[]) value);
      UUID manifest = r.uuid();
      orders
          .computeIfAbsent(manifest, k -> new ArrayList<>())
          .add(new PlannedOrder(r.uuid(), r.uuid(), r.text(), r.text(), r.text()));
    }
    var result = new ArrayList<Manifest>();
    for (Object value : selected) {
      var r = new Row((Object[]) value);
      UUID id = r.uuid();
      String status = r.text();
      long version = r.number();
      Instant updated = r.instant();
      boolean hasOrders = r.bool();
      var items = List.copyOf(orders.getOrDefault(id, List.of()));
      // Only visible allocations contribute; hidden Customers cannot affect this indicator.
      boolean ordersTruncated = (hasOrders && items.isEmpty()) || id.equals(partiallyShownManifest);
      if (scope.tenantWide() || !items.isEmpty())
        result.add(new Manifest(id, status, version, updated, items, ordersTruncated));
    }
    // Scoped callers receive no indication or count of hidden allocations/manifests.
    return new ManifestResult(List.copyOf(result), scope.tenantWide() && truncated);
  }

  TimelinePage timeline(
      UUID tenant, UUID trailer, CustomerAccessScope scope, TrailerTimelineQuery input) {
    var sql =
        """
        select e.id,e.event_type,e.actor_type,e.source_type,e.occurred_at,e.recorded_at,e.reason,
        l.id,l.name,e.driver_id,d.name,e.tractor_id,t.operational_number,e.trip_id,e.trip_stop_id,cast(e.metadata as text)
        from trailer_movement_events e
        left join operational_locations l on l.tenant_id=e.tenant_id and l.id=e.location_id %s
        left join drivers d on d.tenant_id=e.tenant_id and d.id=e.driver_id
        left join tractors t on t.tenant_id=e.tenant_id and t.id=e.tractor_id
        where e.tenant_id=:tenant and e.trailer_id=:trailer
        """
                .formatted(
                    scope.tenantWide()
                        ? ""
                        : "and (l.customer_id is null or l.customer_id in (:customers))")
            + (input.position() == null ? "" : " and (e.occurred_at,e.id)<(:occurred,:id)")
            + " order by e.occurred_at desc,e.id desc";
    var q =
        scoped(sql, tenant, scope)
            .setParameter("trailer", trailer)
            .setMaxResults(input.limit() + 1);
    if (input.position() != null)
      q.setParameter("occurred", input.position().occurredAt())
          .setParameter("id", input.position().id());
    var rows = q.getResultList();
    var items = new ArrayList<TimelineItem>();
    for (Object value : rows.subList(0, Math.min(input.limit(), rows.size()))) {
      var r = new Row((Object[]) value);
      UUID id = r.uuid();
      String event = r.text(), actor = r.text(), source = r.text();
      Instant occurred = r.instant(), recorded = r.instant();
      String reason = r.text();
      UUID location = r.uuid();
      String locationName = r.text();
      UUID driver = r.uuid();
      String driverName = r.text();
      UUID tractor = r.uuid();
      String tractorNumber = r.text();
      UUID trip = r.uuid(), stop = r.uuid();
      var changes = changes(r.text(), scope);
      items.add(
          new TimelineItem(
              id,
              event,
              actor,
              source,
              occurred,
              recorded,
              scope.tenantWide() ? reason : null,
              location,
              locationName,
              driver,
              driverName,
              tractor,
              tractorNumber,
              scope.tenantWide() ? trip : null,
              scope.tenantWide() ? stop : null,
              changes));
    }
    String cursor =
        rows.size() > input.limit()
            ? new TrailerTimelineQuery.Position(items.getLast().occurredAt(), items.getLast().id())
                .encode()
            : null;
    return new TimelinePage(List.copyOf(items), cursor);
  }

  private Map<String, Object> changes(String metadata, CustomerAccessScope scope) {
    if (metadata == null) return Map.of();
    var node = json.readTree(metadata);
    var values = new LinkedHashMap<String, Object>();
    var enums =
        Map.of(
            "previousLocationState",
            TrailerValidation.LOCATIONS,
            "previousLoadState",
            TrailerValidation.LOADS,
            "newLoadState",
            TrailerValidation.LOADS,
            "previousServiceabilityState",
            TrailerValidation.SERVICES,
            "newServiceabilityState",
            TrailerValidation.SERVICES);
    enums.forEach(
        (key, catalog) -> {
          var v = node.get(key);
          if (v != null && v.isString() && catalog.contains(v.asString()))
            values.put(key, v.asString());
        });
    if (scope.tenantWide())
      for (String key : List.of("previousLocationId", "newLocationId")) {
        var v = node.get(key);
        if (v == null) continue;
        if (v.isNull()) values.put(key, null);
        else if (v.isString()) {
          try {
            values.put(key, TrailerValidation.uuid(v.asString()));
          } catch (TrailerFailure ignored) {
            /* Unknown details are omitted. */
          }
        }
      }
    return Collections.unmodifiableMap(values);
  }

  private static class Row {
    private final Object[] values;
    private int i;

    Row(Object[] values) {
      this.values = values;
    }

    UUID uuid() {
      return (UUID) values[i++];
    }

    String text() {
      return (String) values[i++];
    }

    long number() {
      return ((Number) values[i++]).longValue();
    }

    boolean bool() {
      return (Boolean) values[i++];
    }

    Instant instant() {
      Object v = values[i++];
      return v == null
          ? null
          : v instanceof Instant x
              ? x
              : v instanceof java.time.OffsetDateTime x
                  ? x.toInstant()
                  : ((java.sql.Timestamp) v).toInstant();
    }
  }
}
