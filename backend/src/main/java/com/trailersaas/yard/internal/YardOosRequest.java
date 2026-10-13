package com.trailersaas.yard.internal;
import java.time.Instant;
import java.util.*;

record YardOosRequest(UUID trailerId,UUID siteId,long expectedTrailerVersion,Instant observedAt,String comment,List<UUID> documentIds) {
  static YardOosRequest parse(Map<String,Object> body) {
    if(body==null || !body.keySet().equals(Set.of("trailerId","siteId","expectedTrailerVersion","observedAt","comment","documentIds"))) throw YardFailure.invalid();
    var version=body.get("expectedTrailerVersion");
    if(!(version instanceof Integer || version instanceof Long) || ((Number)version).longValue()<0) throw YardFailure.invalid();
    if(!(body.get("documentIds") instanceof List<?> ids)) throw YardFailure.invalid();
    if(ids.isEmpty()) throw new YardFailure(409,"EVIDENCE_REQUIRED","At least one finalized damage photograph is required.");
    if(ids.size()>50) throw YardFailure.invalid();
    var documents=ids.stream().map(YardMoveRequest::uuid).sorted().toList();
    if(new HashSet<>(documents).size()!=documents.size()) throw YardFailure.invalid();
    return new YardOosRequest(YardMoveRequest.uuid(body.get("trailerId")),YardMoveRequest.uuid(body.get("siteId")),((Number)version).longValue(),YardActionRequest.time(body.get("observedAt")),YardActionRequest.reason(body.get("comment")),documents);
  }
}
