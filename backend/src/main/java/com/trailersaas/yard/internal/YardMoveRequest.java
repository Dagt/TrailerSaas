package com.trailersaas.yard.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

record YardMoveRequest(UUID trailerId,UUID fromLocationId,UUID toLocationId,Instant observedAt,long expectedTrailerVersion) {
  static YardMoveRequest parse(Map<String,Object> body) {
    if(body==null||!body.keySet().equals(Set.of("trailerId","fromLocationId","toLocationId","observedAt","expectedTrailerVersion")))throw YardFailure.invalid();
    var v=body.get("expectedTrailerVersion");
    if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0)throw YardFailure.invalid();
    Instant at;
    if(!(body.get("observedAt") instanceof String time)||!time.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt].*"))throw YardFailure.invalid();
    try {at=Instant.parse((String)body.get("observedAt"));}catch(RuntimeException e){throw YardFailure.invalid();}
    if(at.isAfter(Instant.now()))throw YardFailure.invalid();
    return new YardMoveRequest(uuid(body.get("trailerId")),uuid(body.get("fromLocationId")),uuid(body.get("toLocationId")),at.truncatedTo(ChronoUnit.MICROS),((Number)v).longValue());
  }
  static UUID uuid(Object raw) {
    if(!(raw instanceof String value)||!value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw YardFailure.invalid();
    try {return UUID.fromString(value);}catch(RuntimeException e){throw YardFailure.invalid();}
  }
}
