package com.trailersaas.identity.internal;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.Capability;
import com.trailersaas.identity.TenantAuthorization;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.trailersaas.audit.AuditWriter;
import static com.trailersaas.audit.AuditWriter.snapshot;
import static com.trailersaas.audit.AuditWriter.Action.TENANT_SETTINGS_UPDATED;

@Service @Transactional
class TenantSettingsService {
    private final EntityManager entityManager;
    private final TenantAuthorization authorization;
    private final AuditWriter audit;
    TenantSettingsService(EntityManager entityManager, TenantAuthorization authorization, AuditWriter audit) {
        this.entityManager = entityManager; this.authorization = authorization;
        this.audit = audit;
    }
    record Settings(int staleTrailerThresholdHours, String defaultTimezone, long version) {
        static Settings of(TenantSettingsEntity row) {
            return new Settings(row.staleTrailerThresholdHours, row.defaultTimezone, row.version);
        }
    }
    @Transactional(readOnly = true)
    public Settings get(AuthenticatedUserPrincipal user, UUID tenantId) {
        return Settings.of(resource(user, tenantId));
    }
    public Settings patch(AuthenticatedUserPrincipal user, UUID tenantId, Map<String, Object> fields) {
        var row = resource(user, tenantId);
        long expected = validatePatch(fields);
        if (row.version != expected) { throw TenantSettingsFailure.stale(); }
        var before = auditState(row);
        if (fields.containsKey("staleTrailerThresholdHours")) {
            row.staleTrailerThresholdHours = threshold(fields.get("staleTrailerThresholdHours"));
        }
        if (fields.containsKey("defaultTimezone")) { row.defaultTimezone = timezone(fields.get("defaultTimezone")); }
        entityManager.flush(); // @Version detects races; unchanged values remain a no-op.
        audit.human(tenantId,user.userId(),TENANT_SETTINGS_UPDATED,tenantId,before,auditState(row));
        return Settings.of(row);
    }
    private static Map<String,Object> auditState(TenantSettingsEntity row) {
        return snapshot("staleTrailerThresholdHours",row.staleTrailerThresholdHours,"defaultTimezone",row.defaultTimezone,"version",row.version);
    }
    private TenantSettingsEntity resource(AuthenticatedUserPrincipal user, UUID tenantId) {
        authorization.requireTenantCapability(user, tenantId, Capability.TENANT_SETTINGS_MANAGE);
        return entityManager.createQuery("select s from TenantSettingsEntity s where s.tenantId = :tenant", TenantSettingsEntity.class)
                .setParameter("tenant", tenantId).getResultStream().findFirst().orElseThrow(TenantSettingsFailure::missing);
    }
    static long validatePatch(Map<String, Object> fields) {
        if (fields == null || !Set.of("expectedVersion", "staleTrailerThresholdHours", "defaultTimezone").containsAll(fields.keySet())) {
            throw TenantSettingsFailure.invalid("body");
        }
        long version = integer(fields.get("expectedVersion"), "expectedVersion");
        if (version < 0) { throw TenantSettingsFailure.invalid("expectedVersion"); }
        return version;
    }
    static long integer(Object value, String field) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw TenantSettingsFailure.invalid(field);
        }
        return ((Number) value).longValue();
    }
    static int threshold(Object value) {
        long n = integer(value, "staleTrailerThresholdHours");
        if (n < 1 || n > Integer.MAX_VALUE) { throw TenantSettingsFailure.invalid("staleTrailerThresholdHours"); }
        return (int) n;
    }
    static String timezone(Object value) {
        if (value == null) { return null; }
        if (!(value instanceof String zone) || !ZoneId.getAvailableZoneIds().contains(zone)) {
            throw TenantSettingsFailure.invalid("defaultTimezone");
        }
        return zone;
    }
}
