package com.trailersaas.trailers;

import java.util.UUID;
import com.trailersaas.trailers.TrailerMovementProjection.*;

/** Transaction-joining serviceability transition shared by administrative and evidenced Yard callers. */
public interface TrailerServiceabilityCommands {
  record OutOfService(Target target,Context context,String comment) {}
  Result markOutOfService(UUID tenantId,OutOfService command);
}
