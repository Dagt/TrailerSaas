package com.trailersaas.trips.internal;

import static com.trailersaas.trips.internal.TripExecutionRequests.*;

import com.trailersaas.customers.TripDestinationPolicy;
import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.*;
import com.trailersaas.trips.TripDeparturePrerequisites;
import com.trailersaas.trips.DriverDeliveryExecution;
import com.trailersaas.trips.DriverOperationRejectedException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
class DriverTripExecutionService implements DriverDeliveryExecution {
  private final TenantAuthorization auth;
  private final TenantContext context;
  private final DriverIdentityPolicy drivers;
  private final TripRepository repository;
  private final DriverWorkService reads;
  private final TrailerTripExecution physical;
  private final TripDestinationPolicy destinations;
  private final TripDeparturePrerequisites prerequisites;
  private final MobileOperationLedger ledger;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final com.trailersaas.orders.OrderFulfillment fulfillment;

  DriverTripExecutionService(
      TenantAuthorization auth,
      TenantContext context,
      DriverIdentityPolicy drivers,
      TripRepository repository,
      DriverWorkService reads,
      TrailerTripExecution physical,
      TripDestinationPolicy destinations,
      TripDeparturePrerequisites prerequisites,
      MobileOperationLedger ledger,
      JdbcTemplate jdbc,
      JsonMapper json, com.trailersaas.orders.OrderFulfillment fulfillment) {
    this.auth = auth;
    this.context = context;
    this.drivers = drivers;
    this.repository = repository;
    this.reads = reads;
    this.physical = physical;
    this.destinations = destinations;
    this.prerequisites = prerequisites;
    this.ledger = ledger;
    this.jdbc = jdbc;
    this.json = json;
    this.fulfillment = fulfillment;
  }

  record Detail(
      DriverWorkService.Work trip,
      String tractorOperationalNumber,
      TrailerTripExecution.Coupled currentCoupling,
      Instant startedAt,
      Instant completedAt,
      boolean departurePrerequisitesSatisfied) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Detail get(AuthenticatedUserPrincipal actor, UUID tenant, UUID id) {
    context.establishTenant(tenant);
    var driver = drivers.activeForUser(tenant, actor.userId());
    auth.requireAssignedWorkCapability(
        actor, tenant, Capability.TRIP_VIEW_ASSIGNED, driver.isPresent());
    var trip = repository.resource(tenant, id, false);
    if (!driver.orElseThrow(TripFailure::missing).equals(trip.currentDriverId))
      throw TripFailure.missing();
    return detail(trip);
  }

  Detail command(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID resource,
      Action action,
      String key,
      Map<String, Object> body) {
    var r = parse(action, body);
    var operation = TripCouplingValidation.key(key);
    context.establishTenant(tenant);
    // Resolve Stop's parent read-only first; then all writes follow Trip -> Driver -> Stop ->
    // Tractor -> Trailer.
    UUID tripId =
        action == Action.FIRST_DEPARTURE
            ? resource
            : jdbc
                .query(
                    "select trip_id from trip_stops where tenant_id=? and id=?",
                    (rs, n) -> rs.getObject(1, UUID.class),
                    tenant,
                    resource)
                .stream()
                .findFirst()
                .orElseThrow(TripFailure::missing);
    var lockedOrders=action==Action.FIRST_DEPARTURE?fulfillment.lockTripOrders(tenant,tripId):List.<UUID>of();
    var trip = repository.resource(tenant, tripId, true);
    var driver = drivers.lockActiveForUser(tenant, actor.userId());
    auth.requireAssignedWorkCapability(
        actor, tenant, Capability.TRIP_EXECUTE_ASSIGNED, driver.isPresent());
    if (!driver.orElseThrow(TripFailure::missing).equals(trip.currentDriverId))
      throw TripFailure.missing();
    if (action == Action.FIRST_DEPARTURE || action == Action.DEPARTURE)
      auth.requireTenantCapability(actor, tenant, Capability.DEPARTURE_CONFIRM);
    if (action == Action.ARRIVAL)
      auth.requireTenantCapability(actor, tenant, Capability.ARRIVAL_CONFIRM);
    String hash = hash(action, resource, r);
    try {
      return ledger.execute(
          tenant,
          actor.userId(),
          operation,
          "TRIP_EXECUTION_" + action,
          hash,
          "TRIP",
          d -> d.trip().id(),
          Detail.class,
          () -> {
            var stops = repository.lockedStops(tenant, tripId);
            if (stops.isEmpty()) throw TripFailure.tripState();
            if (r.occurredAt().isAfter(Instant.now())) throw TripFailure.invalid("occurredAt");
            var active =
                stops.stream()
                    .filter(s -> !Set.of("COMPLETED", "CANCELLED").contains(s.status))
                    .findFirst()
                    .orElse(null);
            var stop =
                action == Action.FIRST_DEPARTURE
                    ? active
                    : stops.stream()
                        .filter(s -> s.id.equals(resource))
                        .findFirst()
                        .orElseThrow(TripFailure::missing);
            var previous = trip.status;
            switch (action) {
              case FIRST_DEPARTURE -> {
                state(trip, "ASSIGNED");
                if (active == null
                    || !"PENDING".equals(active.status)
                    || r.fromStopId() != null
                    || !active.id.equals(r.nextStopId())) throw TripFailure.tripState();
                if (trip.currentTractorId == null
                    || !prerequisites.satisfiedAt(
                        tenant, driver.get(), trip.currentTractorId, tripId,r.occurredAt()))
                  throw TripFailure.conflict();
                physical.depart(
                    tenant,
                    tripId,
                    driver.get(),
                    trip.currentTractorId,
                    trip.plannedInitialTrailerId,
                    movement(actor, trip, active, driver.get(), operation, r.occurredAt()));
                trip.status = "IN_TRANSIT";
                trip.startedAt = r.occurredAt();
                fulfillment.started(tenant,lockedOrders);
                active.status = "EN_ROUTE";
                event(actor, trip, active, "ACTIVATED", operation, r, previous);
              }
              case ARRIVAL -> {
                state(trip, "IN_TRANSIT");
                requiredActive(active, stop, "EN_ROUTE");
                chronology(r.occurredAt(), trip.startedAt);
                stops.stream()
                    .filter(s -> s.sequenceNumber < stop.sequenceNumber && s.departedAt != null)
                    .map(s -> s.departedAt)
                    .max(Comparator.naturalOrder())
                    .ifPresent(at -> chronology(r.occurredAt(), at));
                if (stop.locationId != null
                    && !destinations.compatible(
                        tenant,
                        repository.stopCustomers(tenant, tripId, stop.id),
                        stop.siteId,
                        stop.locationId)) throw TripFailure.conflict();
                physical.arrive(
                    tenant,
                    tripId,
                    driver.get(),
                    trip.currentTractorId,
                    stop.locationId,
                    movement(actor, trip, stop, driver.get(), operation, r.occurredAt()));
                stop.status = "ARRIVED";
                stop.arrivedAt = r.occurredAt();
                trip.status = "AT_STOP";
                event(actor, trip, stop, "ARRIVAL_CONFIRMED", operation, r, previous);
              }
              case SERVICE -> {
                state(trip, "AT_STOP");
                requiredActive(active, stop, "ARRIVED");
                chronology(r.occurredAt(), stop.arrivedAt);
                stop.status = "IN_SERVICE";
                stop.serviceStartedAt = r.occurredAt();
                event(actor, trip, stop, "SERVICE_STARTED", operation, r, previous);
              }
              case COMPLETE -> {
                state(trip,"AT_STOP");requiredActive(active,stop,"IN_SERVICE");chronology(r.occurredAt(),stop.serviceStartedAt);
                if ("DELIVERY".equals(stop.stopType))
                  throw TripFailure.physical("EVIDENCE_REQUIRED");
                completeStop(actor,trip,stops,active,stop,operation,r);
              }
              case DEPARTURE -> {
                state(trip, "AT_STOP");
                if (!"COMPLETED".equals(stop.status)
                    || stop.departedAt != null
                    || active == null
                    || !active.id.equals(r.nextStopId())
                    || !"PENDING".equals(active.status)) throw TripFailure.tripState();
                var previousStop =
                    stops.stream()
                        .filter(
                            s ->
                                s.sequenceNumber < active.sequenceNumber
                                    && !"CANCELLED".equals(s.status))
                        .reduce((a, b) -> b)
                        .orElseThrow(TripFailure::tripState);
                if (!previousStop.id.equals(stop.id)) throw TripFailure.tripState();
                chronology(r.occurredAt(), stop.completedAt);
                physical.depart(
                    tenant,
                    tripId,
                    driver.get(),
                    trip.currentTractorId,
                    trip.plannedInitialTrailerId,
                    movement(actor, trip, stop, driver.get(), operation, r.occurredAt()));
                stop.departedAt = r.occurredAt();
                active.status = "EN_ROUTE";
                active.updatedAt = advance(active.updatedAt);
                trip.status = "IN_TRANSIT";
                event(actor, trip, stop, "DEPARTED", operation, r, previous);
                event(actor, trip, active, "ACTIVATED", operation, r, previous);
              }
            }
            trip.updatedAt = advance(trip.updatedAt);
            stop.updatedAt = advance(stop.updatedAt);
            repository.flush();
            return detail(trip);
          });
    } catch (RuntimeException e) {
      if (TripFailure.integrity(e)) throw TripFailure.conflict();
      throw e;
    }
  }

  public Assignment prepare(AuthenticatedUserPrincipal actor,UUID tenant,UUID stop) {
    try {
      context.establishTenant(tenant);
      UUID id=jdbc.query("SELECT trip_id FROM trip_stops WHERE tenant_id=? AND id=?",(r,n)->r.getObject(1,UUID.class),tenant,stop).stream().findFirst().orElseThrow(TripFailure::missing);
      var trip=repository.resource(tenant,id,true);
      var driver=drivers.lockActiveForUser(tenant,actor.userId());
      auth.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_EXECUTE_ASSIGNED,driver.isPresent());
      auth.requireTenantCapability(actor,tenant,Capability.POD_UPLOAD);
      if(!driver.orElseThrow(TripFailure::missing).equals(trip.currentDriverId))throw TripFailure.missing();
      repository.lockedStops(tenant,id);
      return new Assignment(trip.id,driver.get(),trip.currentTractorId);
    } catch(TripFailure e){throw new DriverOperationRejectedException(e.status,e.code);}
  }

  public Result complete(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,UUID operation,Instant at) {
    try {
      var assigned=prepare(actor,tenant,id);var trip=repository.resource(tenant,assigned.tripId(),true);
      var stops=repository.lockedStops(tenant,trip.id);
      var stop=stops.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow(TripFailure::missing);
      var active=stops.stream().filter(s->!Set.of("COMPLETED","CANCELLED").contains(s.status)).findFirst().orElse(null);
      if(!"DELIVERY".equals(stop.stopType) || at==null || at.isAfter(Instant.now()))throw TripFailure.tripState();
      completeStop(actor,trip,stops,active,stop,operation,new Command(at,null,null,null,null,null));
      trip.updatedAt=advance(trip.updatedAt);stop.updatedAt=advance(stop.updatedAt);repository.flush();
      return new Result(stop.id,trip.id,stop.status,trip.status,stop.version,trip.version,stop.completedAt);
    } catch(TripFailure e){throw new DriverOperationRejectedException(e.status,e.code);}
  }

  private void completeStop(AuthenticatedUserPrincipal actor,TripEntity trip,List<TripStopEntity> stops,
      TripStopEntity active,TripStopEntity stop,UUID key,Command request) {
    state(trip,"AT_STOP");requiredActive(active,stop,"IN_SERVICE");chronology(request.occurredAt(),stop.serviceStartedAt);
    if(!physical.serviceSatisfied(trip.tenantId,trip.id,stop.id,stop.stopType,stop.serviceStartedAt))throw TripFailure.conflict();
    String previous=trip.status;stop.status="COMPLETED";stop.completedAt=request.occurredAt();
    if(stops.stream().allMatch(s->Set.of("COMPLETED","CANCELLED").contains(s.status))) {
      trip.status="COMPLETED";trip.completedAt=request.occurredAt();
    }
    event(actor,trip,stop,"COMPLETED",key,request,previous);
  }

  private Detail detail(TripEntity t) {
    var labels =
        t.currentTractorId == null
            ? List.<String>of()
            : jdbc.query(
                "select operational_number from tractors where tenant_id=? and id=?",
                (rs, n) -> rs.getString(1),
                t.tenantId,
                t.currentTractorId);
    return new Detail(
        reads.project(t.tenantId, List.of(t)).getFirst(),
        labels.stream().findFirst().orElse(null),
        physical.current(t.tenantId, t.id),
        t.startedAt,
        t.completedAt,
        "ASSIGNED".equals(t.status)
            && prerequisites.satisfied(t.tenantId, t.currentDriverId, t.currentTractorId, t.id));
  }

  private void event(
      AuthenticatedUserPrincipal actor,
      TripEntity trip,
      TripStopEntity stop,
      String type,
      UUID key,
      Command r,
      String previous) {
    var metadata = new LinkedHashMap<String, Object>();
    metadata.put("previousTripState", previous);
    metadata.put("newTripState", trip.status);
    if (r.source() != null) metadata.put("arrivalSource", r.source());
    if (r.coordinates() != null) metadata.put("coordinates", r.coordinates());
    if (r.notes() != null) metadata.put("notes", r.notes());
    jdbc.update(
        "insert into"
            + " trip_stop_events(id,tenant_id,trip_stop_id,trip_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at,client_operation_id,metadata)"
            + " values(?,?,?,?,?,'HUMAN_USER','USER_ACTION',?,?,?,?,cast(? as jsonb))",
        UUID.randomUUID(),
        trip.tenantId,
        stop.id,
        trip.id,
        type,
        actor.userId(),
        java.sql.Timestamp.from(r.occurredAt()),
        java.sql.Timestamp.from(Instant.now()),
        key,
        json.writeValueAsString(metadata));
  }

  private static TrailerMovementProjection.Context movement(
      AuthenticatedUserPrincipal actor,
      TripEntity t,
      TripStopEntity s,
      UUID driver,
      UUID key,
      Instant at) {
    return new TrailerMovementProjection.Context(
        at,
        new TrailerMovementProjection.Provenance(
            TrailerMovementProjection.ActorType.HUMAN_USER,
            TrailerMovementProjection.SourceType.USER_ACTION,
            actor.userId()),
        driver,
        t.id,
        s.id,
        t.currentTractorId,
        key);
  }

  private static Instant advance(Instant prior) {
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    return now.isAfter(prior) ? now : prior.plus(1, ChronoUnit.MICROS);
  }

  private static void state(TripEntity t, String state) {
    if (!state.equals(t.status)) throw TripFailure.tripState();
  }

  private static void requiredActive(TripStopEntity active, TripStopEntity stop, String status) {
    if (active == null || !active.id.equals(stop.id) || !status.equals(stop.status))
      throw TripFailure.tripState();
  }

  private static void chronology(Instant at, Instant prior) {
    if (prior == null || at.isBefore(prior)) throw TripFailure.conflict();
  }

  private String hash(Action action, UUID resource, Command r) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (action + "|" + resource + "|" + json.writeValueAsString(r))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Required digest unavailable.");
    }
  }
}
