package com.trailersaas.trailers.internal;

import static com.trailersaas.identity.Capability.*;
import static com.trailersaas.trailers.TrailerMovementProjection.*;
import static com.trailersaas.trailers.internal.TrailerDtos.*;

import com.trailersaas.customers.CustomerIdentityPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerMovementProjection;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class TrailerAdministrativeCommandService {
  private final TenantAuthorization authorization;
  private final TrailerMovementProjection movement;
  private final TrailerEventWriter writer;
  private final TrailerRepository repository;
  private final CustomerIdentityPolicy customers;
  private final EntityManager em;

  TrailerAdministrativeCommandService(
      TenantAuthorization authorization,
      TrailerMovementProjection movement,
      TrailerEventWriter writer,
      TrailerRepository repository,
      CustomerIdentityPolicy customers,
      EntityManager em) {
    this.authorization = authorization;
    this.movement = movement;
    this.writer = writer;
    this.repository = repository;
    this.customers = customers;
    this.em = em;
  }

  Trailer correct(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID id,
      TrailerCommandRequests.LocationCorrection c) {
    authorization.requireTenantCapability(actor, tenant, TRAILER_LOCATION_CORRECT);
    movement.correctLocation(
        tenant,
        new Correction(
            new Target(id, c.expectedVersion()),
            context(actor),
            c.newLocationId(),
            c.reason(),
            true));
    return current(tenant, id);
  }

  Trailer unknown(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID id,
      TrailerCommandRequests.MarkUnknown c) {
    authorization.requireTenantCapability(actor, tenant, TRAILER_LOCATION_CORRECT);
    movement.markLocationUnknown(
        tenant, new MarkUnknown(new Target(id, c.expectedVersion()), context(actor), c.reason()));
    return current(tenant, id);
  }

  Trailer load(
      AuthenticatedUserPrincipal actor, UUID tenant, UUID id, TrailerCommandRequests.LoadChange c) {
    authorization.requireTenantCapability(actor, tenant, TRAILER_STATE_CORRECT);
    movement.changeLoadState(
        tenant,
        new LoadChange(
            new Target(id, c.expectedVersion()), context(actor), c.loadState(), c.reason()));
    return current(tenant, id);
  }

  Trailer oos(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID id,
      TrailerCommandRequests.OutOfService c) {
    authorization.requireTenantCapability(actor, tenant, TRAILER_OOS_MARK);
    var t = locked(tenant, id, c.expectedVersion());
    if (!"IN_SERVICE".equals(t.serviceabilityState)) throw TrailerFailure.transition();
    writer.markOutOfService(t, context(actor), c.comment());
    t.serviceabilityState = "OUT_OF_SERVICE";
    flush();
    return Trailer.of(t);
  }

  Trailer restore(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID id,
      TrailerCommandRequests.ReturnToService c) {
    authorization.requireTenantCapability(actor, tenant, TRAILER_RETURN_TO_SERVICE);
    var t = locked(tenant, id, c.expectedVersion());
    if (!"OUT_OF_SERVICE".equals(t.serviceabilityState)) throw TrailerFailure.transition();
    writer.returnToService(t, context(actor), c.reason());
    t.serviceabilityState = "IN_SERVICE";
    flush();
    return Trailer.of(t);
  }

  Trailer designate(
      AuthenticatedUserPrincipal actor,
      UUID tenant,
      UUID id,
      TrailerCommandRequests.CustomerDesignation c) {
    authorization.requireTenantCapability(actor, tenant, TRAILER_CUSTOMER_REASSIGN);
    var t = locked(tenant, id, c.expectedVersion());
    if (c.newCustomerId().equals(t.designatedCustomerId)) throw TrailerFailure.conflict();
    if (!customers.exists(tenant, c.newCustomerId())) throw TrailerFailure.missing();
    writer.changeDesignatedCustomer(t, context(actor), c.newCustomerId(), c.reason());
    t.designatedCustomerId = c.newCustomerId();
    flush();
    return Trailer.of(t);
  }

  private TrailerEntity locked(UUID tenant, UUID id, long expected) {
    var t = repository.resource(tenant, id, true);
    em.refresh(t, LockModeType.PESSIMISTIC_WRITE);
    t.requireVersion(expected);
    return t;
  }

  private Trailer current(UUID tenant, UUID id) {
    return Trailer.of(repository.resource(tenant, id, false));
  }

  private void flush() {
    try {
      em.flush();
    } catch (RuntimeException failure) {
      if (TrailerFailure.expectedIntegrity(failure)) throw TrailerFailure.conflict();
      throw failure;
    }
  }

  private Context context(AuthenticatedUserPrincipal actor) {
    return new Context(
        Instant.now().truncatedTo(ChronoUnit.MICROS),
        new Provenance(ActorType.HUMAN_USER, SourceType.USER_ACTION, actor.userId()),
        null,
        null,
        null,
        null,
        null);
  }
}
