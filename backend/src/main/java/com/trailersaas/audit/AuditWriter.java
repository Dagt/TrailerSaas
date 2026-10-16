package com.trailersaas.audit;

import java.util.*;

/** Public transactional port. Callers authorize first and supply only explicit business projections. */
public interface AuditWriter {
    enum ActorType { HUMAN_USER, SYSTEM, INTEGRATION }
    enum SourceType { USER_ACTION, MOBILE_AUTOMATION, SYSTEM_PROCESS, EXTERNAL_INTEGRATION }
    record Actor(ActorType type, SourceType source, UUID userId) {
        public static Actor human(UUID user) { return new Actor(ActorType.HUMAN_USER, SourceType.USER_ACTION, user); }
        public static Actor system() { return new Actor(ActorType.SYSTEM, SourceType.SYSTEM_PROCESS, null); }
        public static Actor integration() { return new Actor(ActorType.INTEGRATION, SourceType.EXTERNAL_INTEGRATION, null); }
    }
    enum EntityType { TENANT_SETTINGS, CUSTOMER, PHYSICAL_SITE, OPERATIONAL_LOCATION, DRIVER, TRACTOR,
        TRAILER, TRAILER_QR_IDENTITY, INVITATION, TENANT_MEMBERSHIP }
    enum Action {
        TENANT_SETTINGS_UPDATED(EntityType.TENANT_SETTINGS),
        CUSTOMER_CREATED(EntityType.CUSTOMER), CUSTOMER_UPDATED(EntityType.CUSTOMER),
        CUSTOMER_SITE_ASSOCIATED(EntityType.CUSTOMER), CUSTOMER_SITE_DISASSOCIATED(EntityType.CUSTOMER),
        SITE_CREATED(EntityType.PHYSICAL_SITE), SITE_UPDATED(EntityType.PHYSICAL_SITE),
        LOCATION_CREATED(EntityType.OPERATIONAL_LOCATION), LOCATION_UPDATED(EntityType.OPERATIONAL_LOCATION),
        DRIVER_CREATED(EntityType.DRIVER), DRIVER_UPDATED(EntityType.DRIVER),
        DRIVER_USER_LINKED(EntityType.DRIVER), DRIVER_USER_UNLINKED(EntityType.DRIVER),
        TRACTOR_CREATED(EntityType.TRACTOR), TRACTOR_UPDATED(EntityType.TRACTOR),
        TRAILER_CREATED(EntityType.TRAILER), TRAILER_UPDATED(EntityType.TRAILER),
        TRAILER_QR_ISSUED(EntityType.TRAILER_QR_IDENTITY), TRAILER_QR_REVOKED(EntityType.TRAILER_QR_IDENTITY),
        INVITATION_CREATED(EntityType.INVITATION), INVITATION_EXPIRED(EntityType.INVITATION),
        INVITATION_REVOKED(EntityType.INVITATION), INVITATION_ACCEPTED(EntityType.INVITATION),
        MEMBERSHIP_CREATED(EntityType.TENANT_MEMBERSHIP);
        private final EntityType entity;
        Action(EntityType entity) { this.entity = entity; }
        public EntityType entityType() { return entity; }
        public boolean creation() { return name().endsWith("_CREATED") || this == TRAILER_QR_ISSUED; }
    }

    /** MANDATORY existing writable transaction; a failure rolls back the required business mutation. */
    void record(UUID tenant, Actor actor, Action action, UUID entityId,
        Map<String,Object> before, Map<String,Object> after, String reason);

    default void human(UUID tenant, UUID user, Action action, UUID entityId,
        Map<String,Object> before, Map<String,Object> after) {
        record(tenant, Actor.human(user), action, entityId, before, after, null);
    }

    /** Explicit nullable fields, never reflection/JPA serialization or a request body. */
    static Map<String,Object> snapshot(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Invalid audit projection");
        var result = new LinkedHashMap<String,Object>();
        for (int i=0; i<pairs.length; i+=2) {
            if (!(pairs[i] instanceof String key) || result.containsKey(key)) throw new IllegalArgumentException("Invalid audit projection");
            result.put(key, pairs[i+1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
