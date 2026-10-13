package com.trailersaas.yard.internal;

import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerMovementProjection;
import static com.trailersaas.trailers.TrailerMovementProjection.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
class YardMoveService {
  private final YardContextService reads;
  private final MobileOperationLedger ledger;
  private final TrailerMovementProjection movement;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  YardMoveService(YardContextService reads,MobileOperationLedger ledger,TrailerMovementProjection movement,JdbcTemplate jdbc,JsonMapper json) {
    this.reads=reads;this.ledger=ledger;this.movement=movement;this.jdbc=jdbc;this.json=json;
  }
  record Result(YardDtos.Trailer trailer,Movement movementEvent) {}
  Result move(AuthenticatedUserPrincipal actor,UUID tenant,String key,Map<String,Object> body) {
    // Fresh Tenant/membership/capability validation also pins the RLS context before ledger replay.
    reads.authorize(actor,tenant);
    var r=YardMoveRequest.parse(body);var operation=YardMoveRequest.uuid(key);
    var hash=hash(r);
    return ledger.execute(tenant,actor.userId(),operation,"YARD_MOVE",hash,"TRAILER",result -> result.trailer().trailerId(),Result.class,() -> {
      if(jdbc.query("select id from trailers where tenant_id=? and id=? for update",(rs,n)->rs.getObject(1,UUID.class),tenant,r.trailerId()).isEmpty())throw YardFailure.missing();
      var current=reads.projection(tenant,r.trailerId());
      if(!"AT_LOCATION".equals(current.locationState())||!"IN_SERVICE".equals(current.serviceabilityState())||!"UNCOUPLED".equals(current.couplingState()))
        throw new YardFailure(409,"INVALID_STATE_TRANSITION","Only an active, in-service, stationary uncoupled Trailer can be moved.",current);
      if(!current.currentLocation().id().equals(r.fromLocationId()))throw new YardFailure(409,"LOCATION_MISMATCH","The submitted origin differs from the recorded location. Review without correcting it.",current);
      if(current.version()!=r.expectedTrailerVersion())throw new YardFailure(409,"STALE_VERSION","The Trailer changed. Refresh before reviewing this move.",current);
      if(r.fromLocationId().equals(r.toLocationId()))throw YardFailure.conflict();
      if(current.lastSeenAt()!=null&&r.observedAt().isBefore(current.lastSeenAt()))throw YardFailure.conflict();
      var locations=new LinkedHashMap<UUID,Location>();
      for(var id:List.of(r.fromLocationId(),r.toLocationId()).stream().sorted().toList())locations.put(id,lockedLocation(tenant,id));
      var from=locations.get(r.fromLocationId());var to=locations.get(r.toLocationId());
      if(!from.site().equals(to.site()))throw YardFailure.conflict();
      reads.authorize(actor,tenant); // Recheck authority after potentially waiting on live context locks.
      var c=new Context(r.observedAt(),new Provenance(ActorType.HUMAN_USER,SourceType.USER_ACTION,actor.userId()),null,null,null,null,operation);
      var applied=movement.yardMove(tenant,new YardMove(new Target(r.trailerId(),r.expectedTrailerVersion()),c,r.fromLocationId(),r.toLocationId()));
      return new Result(reads.projection(tenant,r.trailerId()),applied.movementEvent());
    });
  }
  record Location(UUID site,UUID customer) {}
  private Location lockedLocation(UUID tenant,UUID id) {
    // Same Site parent-lock protocol as master-data disassociation. Never lock
    // the SELECT/INSERT/DELETE-only association table or grant it new privileges.
    var rows=jdbc.query("select l.site_id,l.customer_id,l.active,s.active from operational_locations l join physical_sites s on s.tenant_id=l.tenant_id and s.id=l.site_id where l.tenant_id=? and l.id=? for share of l,s",
        (r,n)->{if(!r.getBoolean(3)||!r.getBoolean(4))throw YardFailure.conflict();return new Location(r.getObject(1,UUID.class),r.getObject(2,UUID.class));},tenant,id);
    if(rows.isEmpty())throw YardFailure.missing();var location=rows.getFirst();
    if(location.customer()!=null){
      var active=jdbc.query("select status from customers where tenant_id=? and id=? for share",(r,n)->r.getString(1),tenant,location.customer());
      if(active.isEmpty()||!"ACTIVE".equals(active.getFirst())||!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from customer_sites where tenant_id=? and site_id=? and customer_id=?)",Boolean.class,tenant,location.site(),location.customer())))throw YardFailure.conflict();
    }
    return location;
  }
  private String hash(YardMoveRequest r) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(r).getBytes(StandardCharsets.UTF_8)));}
    catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
}
