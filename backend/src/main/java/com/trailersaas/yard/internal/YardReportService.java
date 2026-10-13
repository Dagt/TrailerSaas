package com.trailersaas.yard.internal;
import com.trailersaas.identity.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
class YardReportService {
  private final YardContextService reads;
  private final YardActionService actions;
  private final YardMoveService locations;
  private final MobileOperationLedger ledger;
  private final TenantAuthorization auth;
  private final JdbcTemplate jdbc;
  YardReportService(YardContextService reads,YardActionService actions,YardMoveService locations,
                    MobileOperationLedger ledger,TenantAuthorization auth,JdbcTemplate jdbc) {
    this.reads=reads;this.actions=actions;this.locations=locations;this.ledger=ledger;this.auth=auth;this.jdbc=jdbc;
  }
  record Report(UUID id,UUID trailerId,UUID siteId,UUID recordedLocationId,UUID reportedLocationId,long observedTrailerVersion,
                UUID actorUserId,String reason,Instant observedAt,Instant recordedAt,UUID clientOperationId) {}
  record Result(YardDtos.Trailer trailer,Report report) {}
  Result create(AuthenticatedUserPrincipal actor,UUID tenant,String key,Map<String,Object> body) {
    reads.authorize(actor,tenant);var r=YardReportRequest.parse(body);var operation=YardMoveRequest.uuid(key);
    return ledger.execute(tenant,actor.userId(),operation,"DISCREPANCY_REPORT",actions.hash(r),"YARD_REPORT",result->result.report().id(),Result.class,()->{
      var trailer=actions.lockTrailer(tenant,r.trailerId());actions.lockSite(tenant,r.siteId());
      if(!locations.lockedLocation(tenant,r.reportedLocationId()).site().equals(r.siteId())) throw YardFailure.missing();
      if(r.recordedLocationId()!=null && jdbc.query("select id from operational_locations where tenant_id=? and id=?",(row,n)->row.getObject(1,UUID.class),tenant,r.recordedLocationId()).isEmpty()) throw YardFailure.missing();
      if(Objects.equals(r.recordedLocationId(),r.reportedLocationId())) throw YardFailure.conflict();
      reads.authorize(actor,tenant);var id=UUID.randomUUID();var recorded=Instant.now().truncatedTo(ChronoUnit.MICROS);
      jdbc.update("insert into yard_discrepancy_reports(id,tenant_id,trailer_id,site_id,recorded_location_id,reported_location_id,observed_trailer_version,actor_user_id,reason,occurred_at,recorded_at,client_operation_id) values(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,tenant,r.trailerId(),r.siteId(),r.recordedLocationId(),r.reportedLocationId(),r.observedTrailerVersion(),actor.userId(),r.reason(),java.sql.Timestamp.from(r.observedAt()),java.sql.Timestamp.from(recorded),operation);
      return new Result(trailer,new Report(id,r.trailerId(),r.siteId(),r.recordedLocationId(),r.reportedLocationId(),r.observedTrailerVersion(),actor.userId(),r.reason(),r.observedAt(),recorded,operation));
    });
  }
  /** Bounded administrative review, not a generic audit/notification workflow. */
  @Transactional(readOnly=true)
  YardDtos.Page<Report> review(AuthenticatedUserPrincipal actor,UUID tenant,UUID cursor,int limit) {
    auth.requireTenantCapability(actor,tenant,Capability.TRAILER_VIEW_ALL);
    if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tenants where id=? and status='ACTIVE')",Boolean.class,tenant))) throw new YardFailure(403,"TENANT_ACCESS_DENIED","An active Tenant is required.");
    if(limit<1 || limit>50) throw YardFailure.invalid();
    Instant after=null;
    if(cursor!=null) after=jdbc.query("select recorded_at from yard_discrepancy_reports where tenant_id=? and id=?",(r,n)->r.getTimestamp(1).toInstant(),tenant,cursor).stream().findFirst().orElseThrow(YardFailure::missing);
    var rows=jdbc.query("select * from yard_discrepancy_reports where tenant_id=? "+(cursor==null?"":"and (recorded_at,id)>(?,?) ")+"order by recorded_at,id limit ?",
      (r,n)->new Report(r.getObject("id",UUID.class),r.getObject("trailer_id",UUID.class),r.getObject("site_id",UUID.class),r.getObject("recorded_location_id",UUID.class),r.getObject("reported_location_id",UUID.class),r.getLong("observed_trailer_version"),r.getObject("actor_user_id",UUID.class),r.getString("reason"),r.getTimestamp("occurred_at").toInstant(),r.getTimestamp("recorded_at").toInstant(),r.getObject("client_operation_id",UUID.class)),
      cursor==null?new Object[]{tenant,limit+1}:new Object[]{tenant,java.sql.Timestamp.from(after),cursor,limit+1});
    return new YardDtos.Page<>(rows.stream().limit(limit).toList(),rows.size()>limit?rows.get(limit-1).id():null);
  }
}
