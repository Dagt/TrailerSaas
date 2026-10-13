package com.trailersaas.yard.internal;

import static com.trailersaas.trailers.TrailerMovementProjection.*;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerMovementProjection;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
class YardActionService {
  private final YardContextService reads;
  private final YardMoveService contexts;
  private final TenantAuthorization auth;
  private final MobileOperationLedger ledger;
  private final TrailerMovementProjection movement;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  YardActionService(YardContextService reads,YardMoveService contexts,TenantAuthorization auth,
      MobileOperationLedger ledger,TrailerMovementProjection movement,JdbcTemplate jdbc,JsonMapper json) {
    this.reads=reads;this.contexts=contexts;this.auth=auth;this.ledger=ledger;
    this.movement=movement;this.jdbc=jdbc;this.json=json;
  }
  record Result(YardDtos.Trailer trailer,Movement movementEvent) {}
  Result execute(AuthenticatedUserPrincipal actor,UUID tenant,String key,String type,Map<String,Object> body) {
    authorize(actor,tenant,type);
    var r=YardActionRequest.parse(type,body);var operation=YardMoveRequest.uuid(key);
    return ledger.execute(tenant,actor.userId(),operation,type,hash(r),"TRAILER",result->result.trailer().trailerId(),Result.class,()->{
      var current=lockTrailer(tenant,r.trailerId());
      if(current.version()!=r.expectedTrailerVersion()) throw new YardFailure(409,"STALE_VERSION","The Trailer changed. Review the latest recorded state.",current);
      lockSite(tenant,r.siteId());
      if(type.equals("LOCATION_CORRECTION")) {
        if(!contexts.lockedLocation(tenant,r.newLocationId()).site().equals(r.siteId())) throw YardFailure.missing();
        if(current.currentLocation()!=null && current.currentLocation().id().equals(r.newLocationId())) throw YardFailure.conflict();
        if(current.lastSeenAt()!=null && r.observedAt().isBefore(current.lastSeenAt())) throw YardFailure.conflict();
      } else if(current.currentLocation()==null || !current.currentLocation().siteId().equals(r.siteId())) {
        throw YardFailure.conflict();
      }
      if(type.equals("LOAD_STATE_CHANGE")) contexts.lockedLocation(tenant,current.currentLocation().id());
      authorize(actor,tenant,type);
      var context=new Context(r.observedAt(),new Provenance(ActorType.HUMAN_USER,SourceType.USER_ACTION,actor.userId()),null,null,null,null,operation);
      var target=new Target(r.trailerId(),r.expectedTrailerVersion());
      var applied=type.equals("LOCATION_CORRECTION")
        ?movement.correctLocation(tenant,new Correction(target,context,r.newLocationId(),r.reason(),true))
        :movement.changeLoadState(tenant,new LoadChange(target,context,r.loadState(),r.reason()));
      return new Result(reads.projection(tenant,r.trailerId()),applied.movementEvent());
    });
  }
  void authorize(AuthenticatedUserPrincipal actor,UUID tenant,String type) {
    reads.authorize(actor,tenant);
    auth.requireTenantCapability(actor,tenant,switch(type) {
      case "LOCATION_CORRECTION" -> Capability.TRAILER_LOCATION_CORRECT;
      case "LOAD_STATE_CHANGE" -> Capability.TRAILER_STATE_CORRECT;
      case "OOS" -> Capability.TRAILER_OOS_MARK;
      default -> throw YardFailure.invalid();
    });
  }
  YardDtos.Trailer lockTrailer(UUID tenant,UUID trailer) {
    if(jdbc.query("select id from trailers where tenant_id=? and id=? for update",(r,n)->r.getObject(1,UUID.class),tenant,trailer).isEmpty()) throw YardFailure.missing();
    return reads.projection(tenant,trailer);
  }
  void lockSite(UUID tenant,UUID site) {
    var active=jdbc.query("select active from physical_sites where tenant_id=? and id=? for share",(r,n)->r.getBoolean(1),tenant,site);
    if(active.isEmpty()) throw YardFailure.missing();
    if(!active.getFirst()) throw YardFailure.conflict();
  }
  String hash(Object r) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(r).getBytes(StandardCharsets.UTF_8)));}
    catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
}
