package com.trailersaas.yard.internal;

import com.trailersaas.identity.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
class YardContextService {
  private final TenantAuthorization authorization;
  private final JdbcTemplate jdbc;
  YardContextService(TenantAuthorization authorization, JdbcTemplate jdbc) {
    this.authorization=authorization; this.jdbc=jdbc;
  }
  YardDtos.Page<YardDtos.Site> sites(AuthenticatedUserPrincipal actor, UUID tenant, UUID cursor, int limit) {
    authorize(actor, tenant);
    var sites=jdbc.query("select id,name,timezone,version from physical_sites where tenant_id=? and active "
        + (cursor==null?"":"and id>? ") + "order by id limit ?",
        (r,n) -> new YardDtos.Site(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),List.of(),null),
        cursor==null?new Object[]{tenant,limit+1}:new Object[]{tenant,cursor,limit+1});
    UUID next=sites.size()>limit?sites.get(limit-1).id():null;
    var items=sites.stream().limit(limit).map(s -> {
      var locations=locationPage(tenant,s.id(),null,200);
      return new YardDtos.Site(s.id(),s.name(),s.timezone(),s.version(),locations.items(),locations.nextCursor());
    }).toList();
    return new YardDtos.Page<>(items,next);
  }
  YardDtos.Page<YardDtos.Location> locations(AuthenticatedUserPrincipal actor, UUID tenant, UUID site, UUID cursor, int limit) {
    authorize(actor, tenant);
    if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from physical_sites where tenant_id=? and id=? and active)",Boolean.class,tenant,site))) throw YardFailure.missing();
    return locationPage(tenant,site,cursor,limit);
  }
  YardDtos.Trailer trailer(AuthenticatedUserPrincipal actor, UUID tenant, UUID id) {
    authorize(actor,tenant); return projection(tenant,id);
  }
  void authorize(AuthenticatedUserPrincipal actor, UUID tenant) {
    authorization.requireTenantCapability(actor,tenant,Capability.YARD_MOVE_EXECUTE);
    if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tenants where id=? and status='ACTIVE')",Boolean.class,tenant)))
      throw new YardFailure(403,"TENANT_ACCESS_DENIED","An active Tenant is required for Yard operations.");
  }
  private YardDtos.Page<YardDtos.Location> locationPage(UUID tenant, UUID site, UUID cursor, int limit) {
    var items=jdbc.query("select l.id,l.site_id,l.name,l.type,l.version,c.id,c.name from operational_locations l "
        + "join physical_sites s on s.tenant_id=l.tenant_id and s.id=l.site_id "
        + "left join customers c on c.tenant_id=l.tenant_id and c.id=l.customer_id "
        + "where l.tenant_id=? and l.site_id=? and l.active and s.active "
        + "and (l.customer_id is null or (c.status='ACTIVE' and exists(select 1 from customer_sites cs where cs.tenant_id=l.tenant_id and cs.site_id=l.site_id and cs.customer_id=l.customer_id))) "
        + (cursor==null?"":"and l.id>? ") + "order by l.id limit ?",
        (r,n) -> new YardDtos.Location(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getLong(5),
            r.getObject(6,UUID.class)==null?null:new YardDtos.Customer(r.getObject(6,UUID.class),r.getString(7))),
        cursor==null?new Object[]{tenant,site,limit+1}:new Object[]{tenant,site,cursor,limit+1});
    return new YardDtos.Page<>(items.stream().limit(limit).toList(),items.size()>limit?items.get(limit-1).id():null);
  }
  YardDtos.Trailer projection(UUID tenant,UUID id) {
    return jdbc.query("select t.id,t.operational_number,t.location_state,t.last_known_location_id,t.last_seen_at,t.load_state,t.serviceability_state,t.active,t.version, "
        + "c.id,c.name,l.id,l.name,l.type,l.site_id,s.name,l.active,s.active, "
        + "exists(select 1 from trailer_couplings cp where cp.tenant_id=t.tenant_id and cp.trailer_id=t.id and cp.dropped_at is null) coupled "
        + "from trailers t left join customers c on c.tenant_id=t.tenant_id and c.id=t.designated_customer_id "
        + "left join operational_locations l on l.tenant_id=t.tenant_id and l.id=t.current_location_id "
        + "left join physical_sites s on s.tenant_id=l.tenant_id and s.id=l.site_id where t.tenant_id=? and t.id=? and t.active",
        (r,n) -> trailerRow(r),tenant,id).stream().findFirst().orElseThrow(YardFailure::missing);
  }
  private static YardDtos.Trailer trailerRow(ResultSet r) throws SQLException {
    return new YardDtos.Trailer(r.getObject(1,UUID.class),r.getString(2),r.getObject(10,UUID.class)==null?null:new YardDtos.Customer(r.getObject(10,UUID.class),r.getString(11)),
        r.getString(3),r.getObject(12,UUID.class)==null?null:new YardDtos.RecordedLocation(r.getObject(12,UUID.class),r.getString(13),r.getString(14),r.getObject(15,UUID.class),r.getString(16),r.getBoolean(17),r.getBoolean(18)),
        r.getObject(4,UUID.class),r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),r.getString(6),r.getString(7),r.getBoolean(19)?"COUPLED":"UNCOUPLED",r.getBoolean(8),r.getLong(9));
  }
}
