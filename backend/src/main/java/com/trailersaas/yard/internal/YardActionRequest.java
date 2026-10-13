package com.trailersaas.yard.internal;

import com.trailersaas.trailers.TrailerMovementProjection.LoadState;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Closed mobile corrective inputs; principal/provenance are never accepted. */
record YardActionRequest(String type, UUID trailerId, UUID siteId, long expectedTrailerVersion,
                         Instant observedAt, String reason, UUID newLocationId, LoadState loadState) {
  static YardActionRequest parse(String type, Map<String,Object> body) {
    var fields = new HashSet<>(Set.of("trailerId","siteId","expectedTrailerVersion","observedAt","reason"));
    fields.add(type.equals("LOCATION_CORRECTION") ? "newLocationId" : "loadState");
    if (body == null || !body.keySet().equals(fields)) throw YardFailure.invalid();
    var v = body.get("expectedTrailerVersion");
    if (!(v instanceof Integer || v instanceof Long) || ((Number)v).longValue()<0) throw YardFailure.invalid();
    LoadState load = null;
    if (type.equals("LOAD_STATE_CHANGE")) {
      if (!(body.get("loadState") instanceof String state)) throw YardFailure.invalid();
      try { load=LoadState.valueOf(state); } catch (IllegalArgumentException e) { throw YardFailure.invalid(); }
    }
    return new YardActionRequest(type,YardMoveRequest.uuid(body.get("trailerId")),YardMoveRequest.uuid(body.get("siteId")),
      ((Number)v).longValue(),time(body.get("observedAt")),reason(body.get("reason")),
      type.equals("LOCATION_CORRECTION")?YardMoveRequest.uuid(body.get("newLocationId")):null,load);
  }
  static Instant time(Object raw) {
    if (!(raw instanceof String value) || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt].*")) throw YardFailure.invalid();
    try {
      var time=Instant.parse(value).truncatedTo(ChronoUnit.MICROS);
      if (time.isAfter(Instant.now())) throw YardFailure.invalid();
      return time;
    } catch (RuntimeException e) { throw YardFailure.invalid(); }
  }
  static String reason(Object raw) {
    if (!(raw instanceof String value) || value.length()>4000) throw YardFailure.invalid();
    int start=0,end=value.length();
    while(start<end && space(value.codePointAt(start))) start+=Character.charCount(value.codePointAt(start));
    while(start<end && space(value.codePointBefore(end))) end-=Character.charCount(value.codePointBefore(end));
    if(start==end) throw YardFailure.invalid();
    return value.substring(start,end);
  }
  private static boolean space(int cp) { return cp==0x85 || Character.isWhitespace(cp) || Character.isSpaceChar(cp); }
}
