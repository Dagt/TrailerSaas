package com.trailersaas.trips.internal;

import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.identity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Bounded own-work projection, without administrative histories or unassigned asset browsing. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class DriverWorkService {
  private final TenantAuthorization auth;
  private final TenantContext context;
  private final DriverIdentityPolicy identity;
  private final TripRepository repository;
  private final JdbcTemplate jdbc;
  DriverWorkService(TenantAuthorization auth, TenantContext context, DriverIdentityPolicy identity,
      TripRepository repository, JdbcTemplate jdbc) {
    this.auth=auth; this.context=context; this.identity=identity; this.repository=repository; this.jdbc=jdbc;
  }

  record Trailer(UUID id, String operationalNumber, String locationState, String loadState,
      String serviceabilityState, boolean active, long version) {}
  record Stop(UUID id, int sequenceNumber, String stopType, UUID siteId, UUID locationId,
      String siteName, String locationName, Map<String,Object> siteSnapshot, Instant scheduledAt,
      String status, String instructions, long version, List<UUID> orderIds) {}
  record Work(UUID id, String status, UUID currentDriverId, UUID currentTractorId,
      UUID plannedInitialTrailerId, Trailer plannedTrailer, long version, Instant updatedAt, List<Stop> stops) {}
  record Page(List<Work> items, String nextCursor) {}

  Page list(AuthenticatedUserPrincipal actor, UUID tenant, Map<String,String> query) {
    TripValidation.fields(query, Set.of("cursor","limit"));
    var f=TripValidation.filters(query);
    context.establishTenant(tenant);
    var driver=identity.activeForUser(tenant,actor.userId());
    auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,driver.isPresent());
    var rows=repository.assignedWork(tenant,driver.orElseThrow(TripFailure::missing),f.cursor(),f.limit());
    boolean more=rows.size()>f.limit();
    var page=more?rows.subList(0,f.limit()):rows;
    if(page.isEmpty()) return new Page(List.of(),null);
    var ids=page.stream().map(t->t.id).toList();
    var stops=repository.stopsForTrips(tenant,ids);
    var mappings=repository.mappingsForTrips(tenant,ids);
    var grouped=new HashMap<UUID,List<UUID>>();
    for(var a:mappings) grouped.computeIfAbsent(a.tripStopId,ignored->new ArrayList<>()).add(a.orderId);
    // Fixed batch label queries; no Java dependency on foreign internals, no event replay.
    var labels=jdbc.queryForList("select s.id,p.name site_name,l.name location_name from trip_stops s left join physical_sites p on p.tenant_id=s.tenant_id and p.id=s.site_id left join operational_locations l on l.tenant_id=s.tenant_id and l.id=s.location_id where s.tenant_id=? and s.trip_id=any(?)", tenant, ids.toArray(UUID[]::new));
    var labelById=new HashMap<UUID,Map<String,Object>>();
    for(var label:labels) labelById.put((UUID)label.get("id"),label);
    var trailerIds=page.stream().map(t->t.plannedInitialTrailerId).filter(Objects::nonNull).distinct().toArray(UUID[]::new);
    var trailers=jdbc.query("select id,operational_number,location_state,load_state,serviceability_state,active,version from trailers where tenant_id=? and id=any(?)",(r,n)->new Trailer(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBoolean(6),r.getLong(7)),tenant,trailerIds);
    var trailerById=new HashMap<UUID,Trailer>();
    for(var t:trailers) trailerById.put(t.id(),t);
    var work=page.stream().map(t->new Work(t.id,t.status,t.currentDriverId,t.currentTractorId,t.plannedInitialTrailerId,trailerById.get(t.plannedInitialTrailerId),t.version,t.updatedAt,stops.stream().filter(s->s.tripId.equals(t.id)).map(s->{
      var l=labelById.get(s.id);
      return new Stop(s.id,s.sequenceNumber,s.stopType,s.siteId,s.locationId,(String)l.get("site_name"),(String)l.get("location_name"),s.siteSnapshot,s.scheduledAt,s.status,s.instructions,s.version,List.copyOf(new LinkedHashSet<>(grouped.getOrDefault(s.id,List.of()))));
    }).toList())).toList();
    return new Page(work,more?new TripValidation.Position(page.getLast().createdAt,page.getLast().id).encode():null);
  }
}
