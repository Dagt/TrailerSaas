package com.trailersaas.identity.internal;

import com.trailersaas.audit.*;
import com.trailersaas.identity.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;

/** Identity HTTP adapter keeps audit a leaf: identity mutations can use its public writer without cycles. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/audit-events")
@Transactional(readOnly=true)
class AuditReadController {
    private final TenantAuthorization authorization;
    private final AuditReader reader;
    private final JdbcTemplate db;
    AuditReadController(TenantAuthorization authorization,AuditReader reader,JdbcTemplate db) { this.authorization=authorization;this.reader=reader;this.db=db; }
    private void authorize(AuthenticatedUserPrincipal actor,UUID tenant) {
        authorization.requireTenantCapability(actor,tenant,Capability.AUDIT_VIEW);
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from tenants where id=? and status='ACTIVE')",Boolean.class,tenant)))throw new AuthorizationDeniedException(AuthorizationDeniedException.Reason.TENANT_ACCESS_DENIED);
    }
    @GetMapping AuditReader.Page list(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@RequestParam MultiValueMap<String,String> query) {
        authorize(actor,tenantId);if(query.values().stream().anyMatch(v->v.size()!=1))throw AuditQueryFailure.invalid();return reader.list(tenantId,actor.userId(),query.toSingleValueMap());
    }
    @GetMapping("/{auditEventId}") AuditReader.Event detail(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID auditEventId,@RequestParam MultiValueMap<String,String> query) {
        authorize(actor,tenantId);if(!query.isEmpty())throw AuditQueryFailure.invalid();return reader.detail(tenantId,auditEventId);
    }
}
