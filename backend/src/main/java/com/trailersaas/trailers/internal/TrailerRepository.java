package com.trailersaas.trailers.internal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.trailersaas.identity.CustomerAccessScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
class TrailerRepository {
    private final EntityManager manager;
    TrailerRepository(EntityManager manager) { this.manager=manager; }
    TrailerEntity resource(UUID tenant,UUID id,boolean lock) {
        var query=manager.createQuery("select r from TrailerEntity r where r.tenantId=:tenant and r.id=:id",TrailerEntity.class)
                .setParameter("tenant",tenant).setParameter("id",id);
        if(lock) { query.setLockMode(LockModeType.PESSIMISTIC_WRITE); }
        return query.getResultStream().findFirst().orElseThrow(TrailerFailure::missing);
    }
    List<TrailerEntity> list(UUID tenant,CustomerAccessScope scope,Map<String,Object> filters) {
        var sql=new StringBuilder("select r from TrailerEntity r where r.tenantId=:tenant");
        if(!scope.tenantWide()) { sql.append(" and r.designatedCustomerId in :customers"); }
        // Property names are server constants, never interpolated from arbitrary input.
        var properties=Map.of("operationalNumber","normalizedNumber","designatedCustomerId","designatedCustomerId",
                "locationState","locationState","currentLocationId","currentLocationId","loadState","loadState",
                "serviceabilityState","serviceabilityState","active","active");
        filters.keySet().stream().sorted().forEach(k->sql.append(" and r.").append(properties.get(k)).append("=:").append(k));
        sql.append(" order by r.normalizedNumber,r.id");
        var query=manager.createQuery(sql.toString(),TrailerEntity.class).setParameter("tenant",tenant);
        if(!scope.tenantWide()) { query.setParameter("customers",scope.customerIds()); }
        filters.forEach(query::setParameter);
        return query.getResultList();
    }
    List<TrailerQrEntity> qrHistory(UUID tenant,UUID trailer) {
        return manager.createQuery("select q from TrailerQrEntity q where q.tenantId=:tenant and q.trailerId=:trailer order by q.issuedAt desc,q.id",TrailerQrEntity.class)
                .setParameter("tenant",tenant).setParameter("trailer",trailer).getResultList();
    }
    TrailerQrEntity qr(UUID tenant,UUID trailer,UUID id) {
        return manager.createQuery("select q from TrailerQrEntity q where q.tenantId=:tenant and q.trailerId=:trailer and q.id=:id",TrailerQrEntity.class)
                .setParameter("tenant",tenant).setParameter("trailer",trailer).setParameter("id",id)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElseThrow(TrailerFailure::missing);
    }
    /** Sole global lookup: exact canonical token, with SELECT-only RLS context established by the service. */
    TrailerQrEntity activeQrForToken(String token) {
        return manager.createQuery("select q from TrailerQrEntity q where q.publicToken=:token and q.active=true and q.revokedAt is null",TrailerQrEntity.class)
                .setParameter("token",token).getResultStream().findFirst().orElseThrow(TrailerFailure::missing);
    }
    void insert(Object entity) { manager.persist(entity); }
    void flush() { manager.flush(); }
}
