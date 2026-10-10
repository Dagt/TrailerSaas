package com.trailersaas.identity;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;

/** Transaction-local claim and saved logical result. Callers authorize freshly before replay. */
public interface MobileOperationLedger {
  <T> T execute(UUID tenant, UUID user, UUID key, String type, String hash,
      String entityType, Function<T,UUID> entityId, Class<T> responseType, Supplier<T> action);
}
