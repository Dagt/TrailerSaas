package com.trailersaas.drivers.internal;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
class DriverRepository {
    private final EntityManager manager;
    DriverRepository(EntityManager manager) { this.manager=manager; }
    DriverEntity resource(UUID tenant, UUID id, boolean lock) {
        var query=manager.createQuery("select r from DriverEntity r where r.tenantId=:tenant and r.id=:id",DriverEntity.class)
                .setParameter("tenant",tenant).setParameter("id",id);
        if(lock) { query.setLockMode(LockModeType.PESSIMISTIC_WRITE); }
        return query.getResultStream().findFirst().orElseThrow(DriverFailure::missing);
    }
    List<DriverEntity> list(UUID tenant) {
        return manager.createQuery("select r from DriverEntity r where r.tenantId=:tenant order by r.name,r.id",DriverEntity.class)
                .setParameter("tenant",tenant).getResultList();
    }
    void insert(DriverEntity entity) { manager.persist(entity); }
    void flush() { manager.flush(); }
}
