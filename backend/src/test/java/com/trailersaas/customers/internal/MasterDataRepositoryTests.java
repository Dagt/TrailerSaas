package com.trailersaas.customers.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import com.trailersaas.identity.CustomerAccessScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MasterDataRepositoryTests {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void scopedSiteQuerySeparatesOptionalIdAndAssociationPredicates(boolean detail) {
        var em = mock(EntityManager.class);
        TypedQuery<PhysicalSiteEntity> query = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(PhysicalSiteEntity.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var tenant = UUID.randomUUID(); var customer = UUID.randomUUID();
        var site = detail ? UUID.randomUUID() : null;
        new MasterDataRepository(em).sites(tenant, new CustomerAccessScope(false, Set.of(customer)), site);
        var hql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(hql.capture(), eq(PhysicalSiteEntity.class));
        assertThat(hql.getValue()).contains("s.tenantId=:tenant", "and exists", "a.customerId in :customers", "order by s.name, s.id");
        if (detail) {
            assertThat(hql.getValue()).containsPattern(":id\\s+and exists");
            verify(query).setParameter("id", site);
        }
        verify(query).setParameter("tenant", tenant);
        verify(query).setParameter("customers", Set.of(customer));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenantWideSiteQueryDoesNotAcquireCustomerFilters() {
        var em = mock(EntityManager.class);
        TypedQuery<PhysicalSiteEntity> query = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(PhysicalSiteEntity.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        new MasterDataRepository(em).sites(UUID.randomUUID(), new CustomerAccessScope(true, Set.of()), UUID.randomUUID());
        var hql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(hql.capture(), eq(PhysicalSiteEntity.class));
        assertThat(hql.getValue()).contains("s.tenantId=:tenant", "s.id=:id").doesNotContain("customerId", "CustomerSiteEntity");
        verify(query, never()).setParameter(eq("customers"), any());
    }
}
