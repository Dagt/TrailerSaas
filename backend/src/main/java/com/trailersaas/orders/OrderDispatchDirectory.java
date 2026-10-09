package com.trailersaas.orders;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded current Order projection; no history or persistence entity crosses this boundary. */
public interface OrderDispatchDirectory {
  record Pending(
      UUID id,
      UUID customerId,
      String orderNumber,
      String status,
      long version,
      Instant createdAt,
      Instant updatedAt) {}

  List<Pending> pending(AuthenticatedUserPrincipal actor, UUID tenant, UUID customer);
}
