package com.trailersaas.orders;

import com.trailersaas.identity.CustomerAccessScope;
import java.time.Instant;
import java.util.*;

/** Bounded identity-only read SPI for operational planning presence. No Manifest conflation. */
public interface OrderPlanningDirectory {
  record Query(
      UUID tenant,
      CustomerAccessScope scope,
      UUID customer,
      String status,
      Instant before,
      UUID beforeId,
      int limit,
      boolean unplanned) {}

  List<UUID> page(Query query);
}
