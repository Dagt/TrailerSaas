package com.trailersaas.customers.internal;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import com.trailersaas.identity.CustomerAccessScope;
import org.springframework.stereotype.Repository;

@Repository
class MasterDataRepository {
    private final EntityManager em;
    MasterDataRepository(EntityManager em) { this.em = em; }

    <T extends VersionedMasterData> T resource(Class<T> type, UUID tenant, UUID id) {
        return em.createQuery("select r from " + type.getSimpleName() + " r where r.tenantId=:tenant and r.id=:id", type)
                .setParameter("tenant", tenant).setParameter("id", id).getResultStream()
                .findFirst().orElseThrow(MasterDataFailure::missing);
    }

    List<CustomerEntity> customers(UUID tenant, CustomerAccessScope scope) {
        var query = em.createQuery("select c from CustomerEntity c where c.tenantId=:tenant"
                + (scope.tenantWide() ? "" : " and c.id in :customers") + " order by c.name, c.id", CustomerEntity.class)
                .setParameter("tenant", tenant);
        if (!scope.tenantWide()) { query.setParameter("customers", scope.customerIds()); }
        return query.getResultList();
    }

    List<PhysicalSiteEntity> sites(UUID tenant, CustomerAccessScope scope, UUID id) {
        var query = em.createQuery("""
                select s from PhysicalSiteEntity s where s.tenantId=:tenant
                """ + (id == null ? "" : " and s.id=:id")
                + (scope.tenantWide() ? "" : """
                    and exists (select a.id from CustomerSiteEntity a where a.tenantId=:tenant
                        and a.siteId=s.id and a.customerId in :customers)
                    """) + " order by s.name, s.id", PhysicalSiteEntity.class).setParameter("tenant", tenant);
        if (id != null) { query.setParameter("id", id); }
        if (!scope.tenantWide()) { query.setParameter("customers", scope.customerIds()); }
        return query.getResultList();
    }

    List<OperationalLocationEntity> locations(UUID tenant, UUID site, CustomerAccessScope scope) {
        var query = em.createQuery("""
                select l from OperationalLocationEntity l where l.tenantId=:tenant and l.siteId=:site
                """ + (scope.tenantWide() ? "" : " and l.customerId in :customers")
                + " order by l.name, l.id", OperationalLocationEntity.class)
                .setParameter("tenant", tenant).setParameter("site", site);
        if (!scope.tenantWide()) { query.setParameter("customers", scope.customerIds()); }
        return query.getResultList();
    }

    UUID lockAssociation(UUID tenant, UUID customer, UUID site, boolean exclusive) {
        // Row locking requires UPDATE privilege. Lock the parent Site, not the
        // SELECT/INSERT/DELETE-only association, using the same protocol on both paths.
        var parent = em.createNativeQuery("""
                select id from physical_sites where tenant_id=:tenant and id=:site
                """ + (exclusive ? " for update" : " for share"), UUID.class)
                .setParameter("tenant", tenant).setParameter("site", site)
                .setFlushMode(FlushModeType.COMMIT).getResultList();
        if (parent.isEmpty()) { throw MasterDataFailure.missing(); }
        var result = em.createNativeQuery("""
                select id from customer_sites where tenant_id=:tenant and customer_id=:customer and site_id=:site
                """, UUID.class)
                .setParameter("tenant", tenant).setParameter("customer", customer).setParameter("site", site)
                .setFlushMode(FlushModeType.COMMIT).getResultList();
        if (result.isEmpty()) {
            throw exclusive ? MasterDataFailure.missing() : MasterDataFailure.conflict();
        }
        return (UUID) result.getFirst();
    }

    boolean hasActiveLocations(UUID tenant, UUID customer, UUID site) {
        return !em.createQuery("""
                select l.id from OperationalLocationEntity l where l.tenantId=:tenant
                    and l.customerId=:customer and l.siteId=:site and l.active=true
                """, UUID.class).setParameter("tenant", tenant).setParameter("customer", customer)
                .setParameter("site", site).setMaxResults(1).getResultList().isEmpty();
    }

    void deleteAssociation(UUID tenant, UUID id) {
        em.createQuery("delete from CustomerSiteEntity a where a.tenantId=:tenant and a.id=:id")
                .setParameter("tenant", tenant).setParameter("id", id).executeUpdate();
    }

    void insert(Object entity) { em.persist(entity); }
    void flush() { em.flush(); }
}
