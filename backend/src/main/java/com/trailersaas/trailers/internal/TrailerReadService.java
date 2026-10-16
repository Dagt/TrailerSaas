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
  private final CustomerActionAccess actions;

  TrailerReadService(TrailerAccessPolicy access, TrailerReadRepository repository, CustomerActionAccess actions) {
    this.access = access;
    this.repository = repository;
    this.actions = actions;
  }

  Items list(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, String> input) {
    var scope = access.readScope(actor, tenant);
    var raw = new HashMap<>(input);
    String limit = raw.remove("limit"), cursor = raw.remove("cursor");
    var filters = TrailerValidation.filters(raw);
    UUID customer = (UUID) filters.get("designatedCustomerId");
    if (customer != null && !scope.tenantWide() && !scope.customerIds().contains(customer))
      throw TrailerFailure.scope();
    scope = selected(actor, tenant, scope, customer);
    if(limit==null && cursor==null) return repository.list(tenant, scope, filters);
    int size=50;
    if(limit!=null) {
      if(!limit.matches("[1-9][0-9]{0,2}") || (size=Integer.parseInt(limit))>100) throw TrailerFailure.invalid();
    }
    String context=actor.userId()+"|"+tenant+"|trailers|"+scope+"|"+new TreeMap<>(raw);
    List<UUID> after;
    try { after=com.trailersaas.ReadCursor.decode(cursor,context,1); } catch(IllegalArgumentException e) { throw TrailerFailure.invalid(); }
    return repository.page(tenant,scope,filters,after.isEmpty()?null:after.getFirst(),size,context);
  }

  Detail detail(AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer) {
    return detail(actor,tenant,trailer,null);
  }
  Detail detail(AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer, UUID customer) {
    return repository.detail(tenant, trailer, selected(actor,tenant,access.readScope(actor,tenant),customer));
  }

  TimelinePage timeline(
      AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer, Map<String, String> parameters) {
    var raw = new HashMap<>(parameters);
    UUID customer=raw.containsKey("customerId")?TrailerValidation.uuid(raw.remove("customerId")):null;
    var scope = selected(actor,tenant,access.readScope(actor, tenant),customer);
    repository.requireVisible(tenant, trailer, scope);
    return repository.timeline(tenant, trailer, scope, TrailerTimelineQuery.parse(raw));
  }
  private CustomerAccessScope selected(AuthenticatedUserPrincipal actor, UUID tenant, CustomerAccessScope scope, UUID customer) {
    if(customer!=null) {
      var caps=actions.capabilities(actor,tenant,customer);
      if(!caps.contains(Capability.TRAILER_VIEW_ALL) && !caps.contains(Capability.TRAILER_VIEW_CUSTOMER_SCOPED)) throw TrailerFailure.scope();
      if(!scope.tenantWide()&&!scope.customerIds().contains(customer)) throw TrailerFailure.scope();
      return new CustomerAccessScope(false,Set.of(customer));
    }
    if(!scope.tenantWide()) for(var id:scope.customerIds()) actions.capabilities(actor,tenant,id);
    return scope;
  }
}
