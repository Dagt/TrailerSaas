package com.trailersaas.trailers;

import java.util.UUID;

/** Context existence/parent stability only; does not grant OOS or generic Trailer authority. */
public interface TrailerEvidenceDirectory {
  boolean lockExists(UUID tenant, UUID trailer);
  boolean exists(UUID tenant, UUID trailer);
}
