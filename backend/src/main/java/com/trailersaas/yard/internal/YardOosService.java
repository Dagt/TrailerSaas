package com.trailersaas.yard.internal;
import static com.trailersaas.trailers.TrailerMovementProjection.*;
import com.trailersaas.trailers.TrailerServiceabilityCommands;
import com.trailersaas.documents.OperationalEvidencePolicy;
import com.trailersaas.identity.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
class YardOosService {
  private final YardActionService actions;
  private final YardContextService reads;
  private final YardMoveService locations;
  private final TrailerServiceabilityCommands serviceability;
  private final OperationalEvidencePolicy evidence;
  private final MobileOperationLedger ledger;
  private final JdbcTemplate jdbc;
  YardOosService(YardActionService actions,YardContextService reads,YardMoveService locations,
                TrailerServiceabilityCommands serviceability,OperationalEvidencePolicy evidence,MobileOperationLedger ledger,JdbcTemplate jdbc) {
    this.actions=actions;this.reads=reads;this.locations=locations;this.serviceability=serviceability;this.evidence=evidence;this.ledger=ledger;this.jdbc=jdbc;
  }
  record Result(YardDtos.Trailer trailer,Movement movementEvent,List<UUID> documentIds) {}
  Result execute(AuthenticatedUserPrincipal actor,UUID tenant,String key,Map<String,Object> body) {
    actions.authorize(actor,tenant,"OOS");var r=YardOosRequest.parse(body);var operation=YardMoveRequest.uuid(key);
    var result=ledger.execute(tenant,actor.userId(),operation,"OOS",actions.hash(r),"TRAILER",value->value.trailer().trailerId(),Result.class,()->{
      var current=actions.lockTrailer(tenant,r.trailerId());
      if(current.version()!=r.expectedTrailerVersion()) throw new YardFailure(409,"STALE_VERSION","The Trailer changed. Review its latest recorded state.",current);
      context(tenant,r,current);
      evidence.validateTrailerOos(actor,tenant,r.trailerId(),r.documentIds());
      for(var document:r.documentIds()) {
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from trailer_oos_evidence where tenant_id=? and document_id=?)",Boolean.class,tenant,document))) throw YardFailure.conflict();
      }
      actions.authorize(actor,tenant,"OOS");
      var c=new Context(r.observedAt(),new Provenance(ActorType.HUMAN_USER,SourceType.USER_ACTION,actor.userId()),null,null,null,null,operation);
      var applied=serviceability.markOutOfService(tenant,new TrailerServiceabilityCommands.OutOfService(new Target(r.trailerId(),r.expectedTrailerVersion()),c,r.comment()));
      evidence.recordTrailerOosEvidence(actor,tenant,r.trailerId(),applied.movementEvent().id(),operation,r.documentIds());
      return new Result(reads.projection(tenant,r.trailerId()),applied.movementEvent(),r.documentIds());
    });
    // A historical receipt never bypasses current capability, resource, Site or evidence authority.
    actions.lockTrailer(tenant,r.trailerId());actions.lockSite(tenant,r.siteId());
    evidence.validateTrailerOos(actor,tenant,r.trailerId(),r.documentIds());
    for(var document:r.documentIds()) {
      if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from trailer_oos_evidence where tenant_id=? and trailer_id=? and event_id=? and client_operation_id=? and actor_user_id=? and document_id=?)",Boolean.class,tenant,r.trailerId(),result.movementEvent().id(),operation,actor.userId(),document))) throw YardFailure.missing();
    }
    actions.authorize(actor,tenant,"OOS");return result;
  }
  private void context(UUID tenant,YardOosRequest r,YardDtos.Trailer current) {
    actions.lockSite(tenant,r.siteId());
    if(current.currentLocation()!=null && !locations.lockedLocation(tenant,current.currentLocation().id()).site().equals(r.siteId())) throw YardFailure.conflict();
  }
}
