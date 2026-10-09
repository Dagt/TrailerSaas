package com.trailersaas.trips.internal;

import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerCouplingTransactions;
import com.trailersaas.trips.TripCouplingExecutionPolicy;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TripCouplingService {
  private final TenantAuthorization auth;
  private final DriverIdentityPolicy drivers;
  private final TripCouplingExecutionPolicy trips;
  private final TrailerCouplingTransactions physical;

  TripCouplingService(
      TenantAuthorization auth,
      DriverIdentityPolicy drivers,
      TripCouplingExecutionPolicy trips,
      TrailerCouplingTransactions physical) {
    this.auth = auth;
    this.drivers = drivers;
    this.trips = trips;
    this.physical = physical;
  }

  TrailerCouplingTransactions.Result hook(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID trip,
      String key,
      Map<String, Object> body) {
    var r = TripCouplingValidation.hook(body);
    var operation = TripCouplingValidation.key(key);
    auth.requireTenantCapability(actor, tenant, Capability.TRAILER_HOOK);
    var context = trips.lock(tenant, trip, r.trailerId());
    var driver = drivers.lockActiveForUser(tenant, actor.userId());
    auth.requireAssignedWorkCapability(
        actor,
        tenant,
        Capability.TRIP_EXECUTE_ASSIGNED,
        driver.isPresent() && driver.get().equals(context.currentDriverId()));
    return physical.hook(
        tenant, actor.userId(), operation, context, driver.orElseThrow(TripFailure::missing), r);
  }

  TrailerCouplingTransactions.Result drop(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID trip,
      String key,
      Map<String, Object> body) {
    var r = TripCouplingValidation.drop(body);
    var operation = TripCouplingValidation.key(key);
    auth.requireTenantCapability(actor, tenant, Capability.TRAILER_DROP);
    var context = trips.lock(tenant, trip, r.trailerId());
    var driver = drivers.lockActiveForUser(tenant, actor.userId());
    auth.requireAssignedWorkCapability(
        actor,
        tenant,
        Capability.TRIP_EXECUTE_ASSIGNED,
        driver.isPresent() && driver.get().equals(context.currentDriverId()));
    return physical.drop(
        tenant,
        actor.userId(),
        operation,
        context,
        driver.orElseThrow(TripFailure::missing),
        r,
        () -> trips.validateDropDestination(tenant, trip, r.stopId(), r.locationId()));
  }
}
