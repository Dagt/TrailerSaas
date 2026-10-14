package com.trailersaas.yard.internal;

import static com.trailersaas.yard.internal.YardCheckDtos.*;
import com.trailersaas.identity.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
class YardCheckService {
  private final TenantAuthorization auth;
  private final MobileOperationLedger ledger;
  private final YardActionService helpers;
  private final YardMoveService locations;
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  YardCheckService(TenantAuthorization auth,MobileOperationLedger ledger,YardActionService helpers,YardMoveService locations,JdbcTemplate jdbc,JsonMapper json) {
    this.auth=auth;this.ledger=ledger;this.helpers=helpers;this.locations=locations;this.jdbc=jdbc;this.json=json;
  }
  void authorize(AuthenticatedUserPrincipal actor,UUID tenant) {
    auth.requireTenantCapability(actor,tenant,Capability.YARD_CHECK_EXECUTE);
    if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tenants where id=? and status='ACTIVE')",Boolean.class,tenant)))
      throw new YardFailure(403,"TENANT_ACCESS_DENIED","An active Tenant is required.");
  }
  Detail start(AuthenticatedUserPrincipal actor,UUID tenant,String key,Map<String,Object> body) {
    authorize(actor,tenant);var r=YardCheckRequest.Start.parse(body);var op=YardCheckRequest.uuid(key);
    var result=ledger.execute(tenant,actor.userId(),op,"YARD_CHECK_START",helpers.hash(r),"YARD_CHECK",v->v.check().id(),Detail.class,()->{
      helpers.lockSite(tenant,r.siteId());
      if(r.locationId()!=null) {
        if(!locations.lockedLocation(tenant,r.locationId()).site().equals(r.siteId()))throw YardFailure.missing();
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select type in ('YARD','DOCK') from operational_locations where tenant_id=? and id=?",Boolean.class,tenant,r.locationId())))throw YardFailure.conflict();
      }
      authorize(actor,tenant);
      var id=UUID.randomUUID();
      // The data-modifying CTE and inventory SELECT share ONE statement snapshot.
      // Movement writers are not blocked: this freezes a coherent point-in-time view.
      jdbc.update("""
          with new_check as (
            insert into yard_checks(id,tenant_id,site_id,location_id,status,started_by_user_id,started_at,created_at,updated_at,client_operation_id)
            values(?,?,?,?,'IN_PROGRESS',?,statement_timestamp(),statement_timestamp(),statement_timestamp(),?) returning id,tenant_id,site_id
          )
          insert into yard_check_items(id,tenant_id,yard_check_id,site_id,trailer_id,expected,expected_location_id,recorded_snapshot)
          select gen_random_uuid(),c.tenant_id,c.id,c.site_id,t.id,true,t.current_location_id,
            jsonb_build_object('operationalNumber',t.operational_number,'trailerVersion',t.version,
              'locationState',t.location_state,'currentLocationId',t.current_location_id,
              'locationName',l.name,'locationType',l.type,'siteName',s.name,
              'loadState',t.load_state,'serviceabilityState',t.serviceability_state,
              'couplingState',case when exists(select 1 from trailer_couplings cp where cp.tenant_id=t.tenant_id and cp.trailer_id=t.id and cp.dropped_at is null) then 'COUPLED' else 'UNCOUPLED' end,
              'lastKnownLocationId',t.last_known_location_id,'lastSeenAt',t.last_seen_at)
          from new_check c join physical_sites s on s.tenant_id=c.tenant_id and s.id=c.site_id
          join operational_locations l on l.tenant_id=c.tenant_id and l.site_id=c.site_id
          join trailers t on t.tenant_id=c.tenant_id and t.current_location_id=l.id
          left join customers customer on customer.tenant_id=l.tenant_id and customer.id=l.customer_id
          where t.active and t.location_state='AT_LOCATION' and l.active and s.active and l.type in ('YARD','DOCK')
            and (?::uuid is null or l.id=?::uuid)
            and (l.customer_id is null or (customer.status='ACTIVE' and exists(select 1 from customer_sites cs where cs.tenant_id=l.tenant_id and cs.site_id=l.site_id and cs.customer_id=l.customer_id)))
          """,id,tenant,r.siteId(),r.locationId(),actor.userId(),op,r.locationId(),r.locationId());
      return detailLocked(tenant,id,null,200,false);
    });
    authorize(actor,tenant);return result;
  }
  Detail detail(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,UUID cursor,int limit,boolean expectedOnly,Long expectedVersion) {
    authorize(actor,tenant);lockCheck(tenant,id,false);authorize(actor,tenant);
    var summary=summary(tenant,id);
    if(expectedVersion!=null&&expectedVersion!=summary.version())throw stale();
    if(cursor!=null&&expectedVersion==null)throw YardFailure.invalid();
    return detailLocked(tenant,id,cursor,limit,expectedOnly);
  }
  YardDtos.Page<Summary> list(AuthenticatedUserPrincipal actor,UUID tenant,UUID site,String status,UUID cursor,int limit,Instant from,Instant to) {
    authorize(actor,tenant);
    if(status!=null&&!Set.of("IN_PROGRESS","COMPLETED","CANCELLED").contains(status))throw YardFailure.invalid();
    if(from!=null&&to!=null&&from.isAfter(to))throw YardFailure.invalid();
    var ids=jdbc.query("select id from yard_checks where tenant_id=? and (?::uuid is null or site_id=?) and (?::varchar is null or status=?) and (?::uuid is null or id>?) and (?::timestamptz is null or started_at>=?) and (?::timestamptz is null or started_at<=?) order by id limit ?",
        (r,n)->r.getObject(1,UUID.class),tenant,site,site,status,status,cursor,cursor,from==null?null:Timestamp.from(from),from==null?null:Timestamp.from(from),to==null?null:Timestamp.from(to),to==null?null:Timestamp.from(to),limit+1);
    var values=new ArrayList<Summary>();
    for(var id:ids.stream().limit(limit).toList()){lockCheck(tenant,id,false);values.add(summary(tenant,id));}
    authorize(actor,tenant);return new YardDtos.Page<>(values,ids.size()>limit?ids.get(limit-1):null);
  }
  void lockCheck(UUID tenant,UUID id,boolean write) {
    if(jdbc.query("select id from yard_checks where tenant_id=? and id=? for "+(write?"update":"share"),(r,n)->r.getObject(1,UUID.class),tenant,id).isEmpty())throw YardFailure.missing();
  }
  Summary summary(UUID tenant,UUID id) {
    return jdbc.query("""
        select c.*, (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.expected) expected_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.found) scanned_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.result='MATCHED') matched_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.result='MISSING') missing_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.result='UNEXPECTED') unexpected_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.result='LOCATION_MISMATCH') mismatch_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id and i.result is null) remaining_count,
          (select count(*) from yard_check_items i where i.tenant_id=c.tenant_id and i.yard_check_id=c.id) total_item_count
        from yard_checks c where c.tenant_id=? and c.id=?
        """,(r,n)->new Summary(id,r.getObject("site_id",UUID.class),r.getObject("location_id",UUID.class),r.getString("status"),r.getLong("version"),r.getObject("started_by_user_id",UUID.class),time(r,"started_at"),r.getObject("completed_by_user_id",UUID.class),time(r,"completed_at"),r.getObject("cancelled_by_user_id",UUID.class),time(r,"cancelled_at"),r.getString("cancellation_reason"),r.getLong("expected_count"),r.getLong("scanned_count"),r.getLong("matched_count"),r.getLong("missing_count"),r.getLong("unexpected_count"),r.getLong("mismatch_count"),r.getLong("remaining_count"),r.getLong("total_item_count")),tenant,id).stream().findFirst().orElseThrow(YardFailure::missing);
  }
  Detail detailLocked(UUID tenant,UUID id,UUID cursor,int limit,boolean expectedOnly) {
    var check=summary(tenant,id);
    var items=jdbc.query("select i.*,r.id reconciliation_id from yard_check_items i left join yard_check_reconciliations r on r.tenant_id=i.tenant_id and r.item_id=i.id where i.tenant_id=? and i.yard_check_id=? and (?=false or i.expected) and (?::uuid is null or i.id>?) order by i.id limit ?",
        (r,n)->item(r),tenant,id,expectedOnly,cursor,cursor,limit+1);
    var next=items.size()>limit?items.get(limit-1).id():null;
    return new Detail(check,items.stream().limit(limit).toList(),next,cursor==null&&next==null);
  }
  @SuppressWarnings("unchecked")
  Item item(ResultSet r) throws SQLException {
    return new Item(r.getObject("id",UUID.class),r.getObject("trailer_id",UUID.class),r.getBoolean("expected"),r.getObject("expected_location_id",UUID.class),json.readValue(r.getString("recorded_snapshot"),Map.class),r.getBoolean("found"),r.getString("result"),r.getObject("observed_location_id",UUID.class),time(r,"scanned_at"),r.getObject("scanned_by_user_id",UUID.class),time(r,"recorded_at"),r.getObject("client_operation_id",UUID.class),r.getObject("reconciliation_id",UUID.class));
  }
  static Instant time(ResultSet r,String field) throws SQLException { var t=r.getTimestamp(field);return t==null?null:t.toInstant(); }
  static YardFailure stale() { return new YardFailure(409,"STALE_VERSION","The Yard Check changed. Refresh its authorized state."); }
  record ScanResult(String outcome,Summary check,Item item) {}
  ScanResult scan(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,String key,Map<String,Object> body) {
    authorize(actor,tenant);var r=YardCheckRequest.Scan.parse(id,body);var op=YardCheckRequest.uuid(key);
    var result=ledger.execute(tenant,actor.userId(),op,"YARD_CHECK_SCAN",helpers.hash(r),"YARD_CHECK",v->v.check().id(),ScanResult.class,()->{
      lockCheck(tenant,id,true);var check=summary(tenant,id);inProgress(check);
      var trailer=helpers.lockTrailer(tenant,r.trailerId());
      var location=locations.lockedLocation(tenant,r.observedLocationId());
      if(!location.site().equals(check.siteId()))throw YardFailure.missing();
      if(check.locationId()!=null&&!check.locationId().equals(r.observedLocationId()))throw YardFailure.conflict();
      if(r.scannedAt().isBefore(check.startedAt()))throw YardFailure.invalid();
      authorize(actor,tenant);
      var existing=jdbc.query("select i.*,null::uuid reconciliation_id from yard_check_items i where tenant_id=? and yard_check_id=? and trailer_id=?",(row,n)->item(row),tenant,id,r.trailerId());
      if(!existing.isEmpty()&&existing.getFirst().found())return new ScanResult("ALREADY_SCANNED",check,existing.getFirst());
      UUID itemId;String outcome;
      if(existing.isEmpty()) {
        itemId=UUID.randomUUID();outcome="UNEXPECTED";
        var facts=new LinkedHashMap<String,Object>();
        facts.put("operationalNumber",trailer.operationalNumber());facts.put("trailerVersion",trailer.version());facts.put("locationState",trailer.locationState());
        facts.put("currentLocationId",trailer.currentLocation()==null?null:trailer.currentLocation().id());
        facts.put("loadState",trailer.loadState());facts.put("serviceabilityState",trailer.serviceabilityState());facts.put("couplingState",trailer.couplingState());
        facts.put("lastKnownLocationId",trailer.lastKnownLocationId());facts.put("lastSeenAt",trailer.lastSeenAt());
        jdbc.update("insert into yard_check_items(id,tenant_id,yard_check_id,site_id,trailer_id,expected,recorded_snapshot,found,result,observed_location_id,scanned_at,scanned_by_user_id,recorded_at,client_operation_id) values(?,?,?,?,?,false,?::jsonb,true,'UNEXPECTED',?,?,?,clock_timestamp(),?)",
            itemId,tenant,id,check.siteId(),r.trailerId(),json.writeValueAsString(facts),r.observedLocationId(),Timestamp.from(r.scannedAt()),actor.userId(),op);
      } else {
        itemId=existing.getFirst().id();outcome=existing.getFirst().expectedLocationId().equals(r.observedLocationId())?"MATCHED":"LOCATION_MISMATCH";
        jdbc.update("update yard_check_items set found=true,result=?,observed_location_id=?,scanned_at=?,scanned_by_user_id=?,recorded_at=clock_timestamp(),client_operation_id=? where tenant_id=? and id=?",outcome,r.observedLocationId(),Timestamp.from(r.scannedAt()),actor.userId(),op,tenant,itemId);
      }
      jdbc.update("update yard_checks set version=version+1,updated_at=clock_timestamp() where tenant_id=? and id=?",tenant,id);
      var item=jdbc.query("select i.*,null::uuid reconciliation_id from yard_check_items i where tenant_id=? and id=?",(row,n)->item(row),tenant,itemId).getFirst();
      return new ScanResult(outcome,summary(tenant,id),item);
    });
    authorize(actor,tenant);return result;
  }
  static void inProgress(Summary c) {if(!c.status().equals("IN_PROGRESS"))throw new YardFailure(409,"INVALID_STATE_TRANSITION","The Yard Check is terminal.");}
}
