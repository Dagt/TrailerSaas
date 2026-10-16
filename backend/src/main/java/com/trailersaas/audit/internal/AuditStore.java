package com.trailersaas.audit.internal;

import com.trailersaas.audit.AuditWriter.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class AuditStore {
    final NamedParameterJdbcTemplate db;
    AuditStore(NamedParameterJdbcTemplate db) { this.db=db; }
    void requireContext(UUID tenant,boolean write) {
        if(tenant==null || !TransactionSynchronizationManager.isActualTransactionActive()
            || write && TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Audit requires an existing Tenant transaction");
        if(!Boolean.TRUE.equals(db.queryForObject("select coalesce(nullif(current_setting('app.current_tenant_id',true),'')::uuid=:tenant and nullif(current_setting('app.current_user_id',true),'') is null,false)",Map.of("tenant",tenant),Boolean.class)))
            throw new IllegalStateException("Audit requires the matching pinned Tenant");
    }
    UUID membership(UUID tenant,UUID user) {
        var rows=db.queryForList("select m.id from tenant_memberships m join users u on u.id=m.user_id where m.tenant_id=:tenant and m.user_id=:user and m.status='ACTIVE'",Map.of("tenant",tenant,"user",user),UUID.class);
        if(rows.size()!=1)throw new IllegalStateException("Audit actor is not an active Tenant member");return rows.getFirst();
    }
    void requireEntity(UUID tenant,EntityType type,UUID id) {
        if(id==null)throw AuditCatalog.invalid();
        String table=switch(type) {
            case TENANT_SETTINGS -> "tenant_settings"; case CUSTOMER -> "customers"; case PHYSICAL_SITE -> "physical_sites";
            case OPERATIONAL_LOCATION -> "operational_locations"; case DRIVER -> "drivers"; case TRACTOR -> "tractors";
            case TRAILER -> "trailers"; case TRAILER_QR_IDENTITY -> "trailer_qr_identities";
            case INVITATION -> "membership_invitations"; case TENANT_MEMBERSHIP -> "tenant_memberships";
        };
        String key=type==EntityType.TENANT_SETTINGS?"tenant_id":"id";
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from "+table+" where tenant_id=:tenant and "+key+"=:id)",Map.of("tenant",tenant,"id",id),Boolean.class)))throw new IllegalStateException("Audit entity is outside the pinned Tenant");
    }
}
