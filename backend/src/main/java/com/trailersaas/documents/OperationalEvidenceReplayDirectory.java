package com.trailersaas.documents;

import java.util.UUID;

/** Owning workflow supplies persisted replay facts; these never replace fresh resource authority. */
public interface OperationalEvidenceReplayDirectory {
  boolean fuelCommitted(UUID tenant,UUID user,UUID operation,UUID trip);
  boolean damageCommitted(UUID tenant,UUID user,UUID operation,UUID trip);
}
