package com.trailersaas.notifications.internal;

import java.time.*;

/** Physical time only; no administrative timestamp, location or receivedAt fallback. */
final class StaleCondition {
 enum Result { INACTIVE, NO_OBSERVATION, INVALID_TIMESTAMP, FUTURE, FRESH, STALE }
 static Result evaluate(boolean active,Instant seen,boolean finite,Instant at,int hours){
  if(hours<=0||at==null)throw new IllegalArgumentException("Positive Tenant threshold and evaluation time required");
  if(!active)return Result.INACTIVE;
  if(!finite)return Result.INVALID_TIMESTAMP;
  if(seen==null)return Result.NO_OBSERVATION;
  if(seen.isAfter(at))return Result.FUTURE;
  return Duration.between(seen,at).compareTo(Duration.ofHours(hours))>=0?Result.STALE:Result.FRESH;
 }
}
