package com.trailersaas.yard.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

final class YardCheckRequest {
  static void fields(Map<String,Object> b,String... names) {
    if(b==null||!b.keySet().equals(Set.of(names))) throw YardFailure.invalid();
  }
  static UUID uuid(Object v) { return YardMoveRequest.uuid(v); }
  static UUID optional(Object v) { return v==null?null:uuid(v); }
  static long version(Object v) {
    if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()<0) throw YardFailure.invalid();
    return ((Number)v).longValue();
  }
  static Instant time(Object v) {
    if(!(v instanceof String s)||!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt].*")) throw YardFailure.invalid();
    try {var at=Instant.parse(s);if(at.isAfter(Instant.now()))throw YardFailure.invalid();return at.truncatedTo(ChronoUnit.MICROS);}
    catch(RuntimeException e){throw YardFailure.invalid();}
  }
  static String reason(Object v) {
    if(!(v instanceof String s)) throw YardFailure.invalid();
    s=s.strip();if(s.isBlank()||s.length()>4000) throw YardFailure.invalid();return s;
  }
  record Start(UUID siteId,UUID locationId) {
    static Start parse(Map<String,Object> b) { fields(b,"siteId","locationId");return new Start(uuid(b.get("siteId")),optional(b.get("locationId"))); }
  }
  record Scan(UUID checkId,UUID trailerId,UUID observedLocationId,Instant scannedAt) {
    static Scan parse(UUID id,Map<String,Object> b) {fields(b,"trailerId","observedLocationId","scannedAt");return new Scan(id,uuid(b.get("trailerId")),uuid(b.get("observedLocationId")),time(b.get("scannedAt")));}
  }
  record Terminal(UUID checkId,long expectedVersion,String reason) {
    static Terminal parse(UUID id,boolean cancel,Map<String,Object> b) {
      fields(b,cancel?new String[]{"expectedVersion","reason"}:new String[]{"expectedVersion"});
      return new Terminal(id,version(b.get("expectedVersion")),cancel?YardCheckRequest.reason(b.get("reason")):null);
    }
  }
}
