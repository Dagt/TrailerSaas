package com.trailersaas.trailers;

import java.util.*;

/** Explicit diagnostic, never used by ordinary GET, never writes/repairs or authorizes a caller. */
public interface TrailerProjectionReconciliation {
  enum Status {
    MATCH,
    DRIFT,
    INSUFFICIENT_HISTORY
  }

  enum Field {
    LOCATION_STATE,
    CURRENT_LOCATION,
    LAST_KNOWN_LOCATION,
    LAST_SEEN,
    LAST_DRIVER,
    LOAD_STATE
  }

  record Comparison(Status status, Set<Field> derivedFields, Set<Field> driftFields) {
    public Comparison {
      derivedFields = Set.copyOf(derivedFields);
      driftFields = Set.copyOf(driftFields);
    }
  }

  Comparison compare(UUID tenantId, UUID trailerId);
}
