package com.trailersaas.trailers.internal;

import com.trailersaas.trailers.TrailerMovementProjection.Context;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Fixed typed facts share one chronology/provenance writer under an already-held Trailer lock. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class TrailerEventWriter {
  private final EntityManager em;

  TrailerEventWriter(EntityManager em) {
    this.em = em;
  }

  TrailerMovementEntity movement(TrailerEntity t, Context c, MovementRules.Change change) {
    return append(t, c, change.type(), change.locationId(), change.reason(), change.metadata());
  }

  void markOutOfService(TrailerEntity t, Context c, String comment) {
    append(
        t,
        c,
        "MARKED_OOS",
        null,
        comment,
        Map.of(
            "previousServiceabilityState",
            t.serviceabilityState,
            "newServiceabilityState",
            "OUT_OF_SERVICE"));
  }

  void returnToService(TrailerEntity t, Context c, String reason) {
    append(
        t,
        c,
        "RETURNED_TO_SERVICE",
        null,
        reason,
        Map.of(
            "previousServiceabilityState",
            t.serviceabilityState,
            "newServiceabilityState",
            "IN_SERVICE"));
  }

  void changeDesignatedCustomer(TrailerEntity t, Context c, UUID customer, String reason) {
    var m = new LinkedHashMap<String, Object>();
    m.put("previousDesignatedCustomerId", t.designatedCustomerId);
    m.put("newDesignatedCustomerId", customer);
    append(t, c, "DESIGNATED_CUSTOMER_CHANGED", null, reason, m);
  }

  private TrailerMovementEntity append(
      TrailerEntity t,
      Context c,
      String type,
      UUID location,
      String reason,
      Map<String, Object> metadata) {
    var e = new TrailerMovementEntity();
    e.id = UUID.randomUUID();
    e.tenantId = t.tenantId;
    e.trailerId = t.id;
    e.tripId = c.tripId();
    e.tripStopId = c.tripStopId();
    e.driverId = c.driverId();
    e.tractorId = c.tractorId();
    e.locationId = location;
    e.eventType = type;
    e.reason = reason;
    e.metadata = metadata;
    e.actorType = c.provenance().actorType().name();
    e.sourceType = c.provenance().sourceType().name();
    e.actorUserId = c.provenance().actorUserId();
    e.occurredAt = c.occurredAt();
    e.clientOperationId = c.clientOperationId();
    // Query before dirtying Trailer: a single eventual flush owns exactly one @Version increment.
    var last =
        em.createQuery(
                "select max(e.recordedAt) from TrailerMovementEntity e where e.tenantId=:tenant and"
                    + " e.trailerId=:trailer",
                Instant.class)
            .setParameter("tenant", t.tenantId)
            .setParameter("trailer", t.id)
            .getSingleResult();
    var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    e.recordedAt = last != null && !now.isAfter(last) ? last.plus(1, ChronoUnit.MICROS) : now;
    em.persist(e);
    t.updatedAt = now.isAfter(t.updatedAt) ? now : t.updatedAt.plus(1, ChronoUnit.MICROS);
    return e;
  }
}
