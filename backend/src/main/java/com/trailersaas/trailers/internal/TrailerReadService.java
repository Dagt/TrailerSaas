package com.trailersaas.trailers.internal;

import static com.trailersaas.trailers.internal.TrailerReadDtos.*;

import com.trailersaas.identity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class TrailerReadService {
  private final TrailerAccessPolicy access;
  private final TrailerReadRepository repository;

  TrailerReadService(TrailerAccessPolicy access, TrailerReadRepository repository) {
    this.access = access;
    this.repository = repository;
  }

  Items list(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, String> input) {
    var scope = access.readScope(actor, tenant);
    var filters = TrailerValidation.filters(input);
    UUID customer = (UUID) filters.get("designatedCustomerId");
    if (customer != null && !scope.tenantWide() && !scope.customerIds().contains(customer))
      throw TrailerFailure.scope();
    return repository.list(tenant, scope, filters);
  }

  Detail detail(AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer) {
    return repository.detail(tenant, trailer, access.readScope(actor, tenant));
  }

  TimelinePage timeline(
      AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer, Map<String, String> parameters) {
    var scope = access.readScope(actor, tenant);
    repository.requireVisible(tenant, trailer, scope);
    return repository.timeline(tenant, trailer, scope, TrailerTimelineQuery.parse(parameters));
  }
}
