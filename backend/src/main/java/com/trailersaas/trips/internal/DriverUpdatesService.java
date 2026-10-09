package com.trailersaas.trips.internal;

import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.identity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DriverUpdatesService {
  private final TenantAuthorization auth;
  private final TenantContext context;
  private final DriverIdentityPolicy identity;
  private final TripRepository repository;

  DriverUpdatesService(
      TenantAuthorization auth,
      TenantContext context,
      DriverIdentityPolicy identity,
      TripRepository repository) {
    this.auth = auth;
    this.context = context;
    this.identity = identity;
    this.repository = repository;
  }

  TripContracts.DriverUpdates list(
      AuthenticatedUserPrincipal actor, UUID tenant, Map<String, String> query) {
    TripValidation.fields(query, Set.of("cursor", "limit"));
    var filters = TripValidation.filters(query);
    // Resolve only this authenticated User; fresh assignment capability is checked before returning
    // rows.
    context.establishTenant(tenant);
    var driver = identity.activeForUser(tenant, actor.userId());
    auth.requireAssignedWorkCapability(
        actor, tenant, Capability.TRIP_VIEW_ASSIGNED, driver.isPresent());
    var rows =
        repository.driverUpdates(
            tenant, driver.orElseThrow(TripFailure::missing), filters.cursor(), filters.limit());
    boolean more = rows.size() > filters.limit();
    var page = more ? rows.subList(0, filters.limit()) : rows;
    return new TripContracts.DriverUpdates(
        page.stream().map(TripContracts.DriverUpdate::of).toList(),
        more
            ? new TripValidation.Position(page.getLast().createdAt, page.getLast().id).encode()
            : null);
  }
}
