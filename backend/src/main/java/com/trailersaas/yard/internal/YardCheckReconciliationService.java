package com.trailersaas.yard.internal;

import static com.trailersaas.trailers.TrailerMovementProjection.*;
import com.trailersaas.trailers.TrailerMovementProjection;
import com.trailersaas.notifications.MissingTrailerAlerts;
import com.trailersaas.identity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class YardCheckReconciliationService {
  private final YardCheckService checks;
  private final YardActionService helpers;
  private final YardMoveService locations;
  private final YardContextService reads;
  private final TenantAuthorization auth;
  private final MobileOperationLedger ledger;
  private final TrailerMovementProjection movement;
  private final MissingTrailerAlerts alerts;
  private final JdbcTemplate jdbc;
  YardCheckReconciliationService(YardCheckService checks,YardActionService helpers,YardMoveService locations,YardContextService reads,TenantAuthorization auth,MobileOperationLedger ledger,TrailerMovementProjection movement,MissingTrailerAlerts alerts,JdbcTemplate jdbc) {
    this.checks=checks;this.helpers=helpers;this.locations=locations;this.reads=reads;this.auth=auth;this.ledger=ledger;this.movement=movement;this.alerts=alerts;this.jdbc=jdbc;
  }
  record Request(UUID checkId,UUID itemId,long expectedTrailerVersion,UUID newLocationId,String reason) {
    static Request parse(UUID check,UUID item,boolean unknown,Map<String,Object> body) {
      YardCheckRequest.fields(body,unknown?new String[]{"expectedTrailerVersion","reason"}:new String[]{"expectedTrailerVersion","newLocationId","reason"});
      return new Request(check,item,YardCheckRequest.version(body.get("expectedTrailerVersion")),unknown?null:YardCheckRequest.uuid(body.get("newLocationId")),YardCheckRequest.reason(body.get("reason")));
    }
  }
  record Result(UUID reconciliationId,UUID yardCheckId,UUID itemId,YardDtos.Trailer trailer,Movement movementEvent,UUID alertId) {}
  void authorize(AuthenticatedUserPrincipal actor,UUID tenant) {
    checks.authorize(actor,tenant);auth.requireTenantCapability(actor,tenant,Capability.TRAILER_LOCATION_CORRECT);
  }
  Result reconcile(AuthenticatedUserPrincipal actor,UUID tenant,UUID checkId,UUID itemId,String key,Map<String,Object> body,boolean unknown) {
    authorize(actor,tenant);var r=Request.parse(checkId,itemId,unknown,body);var op=YardCheckRequest.uuid(key);
    var result=ledger.execute(tenant,actor.userId(),op,unknown?"YARD_CHECK_MARK_LOCATION_UNKNOWN":"YARD_CHECK_LOCATION_CORRECTION",helpers.hash(r),"YARD_CHECK_RECONCILIATION",Result::reconciliationId,Result.class,()->{
      checks.lockCheck(tenant,checkId,true);var check=checks.summary(tenant,checkId);
      if(!check.status().equals("COMPLETED"))throw new YardFailure(409,"INVALID_STATE_TRANSITION","Reconciliation requires a completed Yard Check.");
      var items=jdbc.query("select i.*,r.id reconciliation_id from yard_check_items i left join yard_check_reconciliations r on r.tenant_id=i.tenant_id and r.item_id=i.id where i.tenant_id=? and i.yard_check_id=? and i.id=? for update of i",(row,n)->checks.item(row),tenant,checkId,itemId);
      if(items.isEmpty())throw YardFailure.missing();var item=items.getFirst();
      if(item.reconciliationId()!=null)throw YardFailure.conflict();
      if(!Objects.equals(item.result(),unknown?"MISSING":"LOCATION_MISMATCH"))throw new YardFailure(409,"INVALID_STATE_TRANSITION","The finding is not eligible for this reconciliation.");
      var current=helpers.lockTrailer(tenant,item.trailerId());
      if(current.version()!=r.expectedTrailerVersion())throw new YardFailure(409,"STALE_VERSION","The Trailer changed. Review its current state.",current);
      helpers.lockSite(tenant,check.siteId());
      if(unknown) {
        if(!current.locationState().equals("AT_LOCATION")||current.currentLocation()==null||!current.currentLocation().id().equals(item.expectedLocationId()))throw YardFailure.conflict();
        locations.lockedLocation(tenant,item.expectedLocationId());
      } else {
        if(!r.newLocationId().equals(item.observedLocationId()))throw YardFailure.conflict();
        if(!locations.lockedLocation(tenant,r.newLocationId()).site().equals(check.siteId()))throw YardFailure.missing();
        if(current.currentLocation()!=null&&current.currentLocation().id().equals(r.newLocationId()))throw YardFailure.conflict();
      }
      authorize(actor,tenant);
      var at=Instant.now();var context=new Context(at,new Provenance(ActorType.HUMAN_USER,SourceType.USER_ACTION,actor.userId()),null,null,null,null,op);
      var target=new Target(item.trailerId(),r.expectedTrailerVersion());
      var applied=unknown?movement.markLocationUnknown(tenant,new MarkUnknown(target,context,r.reason())):
        movement.correctLocation(tenant,new Correction(target,context,r.newLocationId(),r.reason(),true));
      UUID alert=unknown?alerts.open(new MissingTrailerAlerts.Context(tenant,item.trailerId(),checkId,itemId,applied.movementEvent().id(),actor.userId(),op,at)):null;
      var reconciliation=UUID.randomUUID();
      jdbc.update("insert into yard_check_reconciliations(id,tenant_id,yard_check_id,item_id,trailer_id,action,movement_event_id,alert_id,actor_user_id,reason,occurred_at,recorded_at,client_operation_id) values(?,?,?,?,?,?,?,?,?,?,?,clock_timestamp(),?)",
          reconciliation,tenant,checkId,itemId,item.trailerId(),unknown?"MARK_LOCATION_UNKNOWN":"LOCATION_CORRECTION",applied.movementEvent().id(),alert,actor.userId(),r.reason(),java.sql.Timestamp.from(applied.movementEvent().occurredAt()),op);
      return new Result(reconciliation,checkId,itemId,reads.projection(tenant,item.trailerId()),applied.movementEvent(),alert);
    });
    authorize(actor,tenant);return result;
  }
}
