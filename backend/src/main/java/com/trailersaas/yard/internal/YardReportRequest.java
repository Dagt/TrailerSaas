package com.trailersaas.yard.internal;
import java.time.Instant;
import java.util.*;

record YardReportRequest(UUID trailerId,UUID siteId,UUID recordedLocationId,UUID reportedLocationId,
                         long observedTrailerVersion,Instant observedAt,String reason) {
  static YardReportRequest parse(Map<String,Object> body) {
    if(body==null || !body.keySet().equals(Set.of("trailerId","siteId","recordedLocationId","reportedLocationId","observedTrailerVersion","observedAt","reason"))) throw YardFailure.invalid();
    var version=body.get("observedTrailerVersion");
    if(!(version instanceof Integer || version instanceof Long) || ((Number)version).longValue()<0) throw YardFailure.invalid();
    return new YardReportRequest(YardMoveRequest.uuid(body.get("trailerId")),YardMoveRequest.uuid(body.get("siteId")),
      body.get("recordedLocationId")==null?null:YardMoveRequest.uuid(body.get("recordedLocationId")),
      YardMoveRequest.uuid(body.get("reportedLocationId")),((Number)version).longValue(),
      YardActionRequest.time(body.get("observedAt")),YardActionRequest.reason(body.get("reason")));
  }
}
