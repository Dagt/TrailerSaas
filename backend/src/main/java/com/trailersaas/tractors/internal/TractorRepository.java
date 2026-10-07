package com.trailersaas.tractors.internal;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

@Repository
class TractorRepository {
    private final EntityManager manager;
    TractorRepository(EntityManager manager) { this.manager=manager; }
    TractorEntity resource(UUID tenant, UUID id) {
        var query=manager.createQuery("select r from TractorEntity r where r.tenantId=:tenant and r.id=:id",TractorEntity.class)
                .setParameter("tenant",tenant).setParameter("id",id);

        return query.getResultStream().findFirst().orElseThrow(TractorFailure::missing);
    }
    List<TractorEntity> list(UUID tenant) {
        return manager.createQuery("select r from TractorEntity r where r.tenantId=:tenant order by r.normalizedNumber,r.id",TractorEntity.class)
                .setParameter("tenant",tenant).getResultList();
    }
    void insert(TractorEntity entity) { manager.persist(entity); }
    void flush() { manager.flush(); }
}
