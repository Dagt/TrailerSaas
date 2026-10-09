package com.trailersaas.orders.internal;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.*;
import com.trailersaas.identity.CustomerAccessScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderRepositoryTests {
    @Test @SuppressWarnings("unchecked")
    void listBindsTenantCustomerScopeFiltersAndRealKeysetPosition() {
        var em = mock(EntityManager.class);
        TypedQuery<OrderEntity> query = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(OrderEntity.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        var tenant = UUID.randomUUID(); var customer = UUID.randomUUID();
        var position = new OrderValidation.Position(Instant.now(), UUID.randomUUID());
        new OrderRepository(em).list(tenant, new CustomerAccessScope(false, Set.of(customer)),
                new OrderValidation.Filters(customer, OrderStatus.OPEN, position, 50));
        var hql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(hql.capture(), eq(OrderEntity.class));
        assertThat(hql.getValue()).contains("o.tenantId=:tenant", "o.customerId in :customers",
                "o.customerId=:customer", "o.status=:status", "o.createdAt<:at",
                "o.createdAt=:at and o.id<:id", "order by o.createdAt desc, o.id desc");
        verify(query).setParameter("tenant", tenant);
        verify(query).setParameter("customers", Set.of(customer));
        verify(query).setParameter("customer", customer);
        verify(query).setParameter("at", position.createdAt());
        verify(query).setParameter("id", position.id());
        verify(query).setMaxResults(51);
        verify(query, never()).setFirstResult(anyInt());
    }
    @Test @SuppressWarnings("unchecked")
    void directResourceLookupBindsBothTenantAndCustomerScopeBeforeReturningParent() {
        var em = mock(EntityManager.class);
        TypedQuery<OrderEntity> query = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(OrderEntity.class))).thenReturn(query);
        when(query.getResultStream()).thenReturn(java.util.stream.Stream.empty());
        var tenant = UUID.randomUUID(); var id = UUID.randomUUID(); var customer = UUID.randomUUID();
        assertThatThrownBy(() -> new OrderRepository(em).resource(tenant, id,
                new CustomerAccessScope(false, Set.of(customer)))).isInstanceOf(OrderFailure.class);
        var hql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(hql.capture(), eq(OrderEntity.class));
        assertThat(hql.getValue()).contains("o.tenantId=:tenant", "o.id=:id", "o.customerId in :customers");
        verify(query).setParameter("tenant", tenant);
        verify(query).setParameter("id", id);
        verify(query).setParameter("customers", Set.of(customer));
    }
    @Test void narrowRepositoryExposesNoGenericUnscopedReadApi() {
        assertThat(Modifier.isPublic(OrderRepository.class.getModifiers())).isFalse();
        assertThat(OrderRepository.class.getInterfaces()).isEmpty();
        var reads = Arrays.stream(OrderRepository.class.getDeclaredMethods())
                .filter(m -> !Modifier.isPrivate(m.getModifiers()) && Set.of("resource", "lockForMutation", "lockForAllocation", "list", "cargo", "stops", "hasActiveSiteUse").contains(m.getName())).toList();
        assertThat(reads).hasSize(7);
        for (var read : reads) { assertThat(read.getParameterTypes()[0]).isEqualTo(UUID.class); }
        assertThat(Arrays.stream(OrderRepository.class.getDeclaredMethods()).map(m -> m.getName()))
                .doesNotContain("findAll", "findById");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"mutation","allocation"})
    @SuppressWarnings("unchecked")
    void lockQueriesRetainTenantAndCustomerScopeAndUseCorrectDatabaseLock(String mode) {
        var em=mock(EntityManager.class);TypedQuery<OrderEntity> q=mock(TypedQuery.class,RETURNS_SELF);
        when(em.createQuery(anyString(),eq(OrderEntity.class))).thenReturn(q);
        when(q.getResultStream()).thenReturn(java.util.stream.Stream.of(new OrderEntity()));
        var tenant=UUID.randomUUID();var id=UUID.randomUUID();var customer=UUID.randomUUID();var scope=new CustomerAccessScope(false,Set.of(customer));
        var repo=new OrderRepository(em);
        if(mode.equals("mutation")){repo.lockForMutation(tenant,id,scope);}else{repo.lockForAllocation(tenant,id,scope);}
        verify(q).setParameter("tenant",tenant);verify(q).setParameter("id",id);verify(q).setParameter("customers",Set.of(customer));
        verify(q).setLockMode(mode.equals("mutation")?jakarta.persistence.LockModeType.PESSIMISTIC_WRITE:jakarta.persistence.LockModeType.PESSIMISTIC_READ);
    }
    @Test @SuppressWarnings("unchecked")
    void activeSiteUseBindsTenantCustomerAndSiteAndExcludesOnlyTerminalStates() {
        var em = mock(EntityManager.class);
        TypedQuery<UUID> query = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(UUID.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(UUID.randomUUID()));
        var tenant = UUID.randomUUID(); var customer = UUID.randomUUID(); var site = UUID.randomUUID();
        assertThat(new OrderRepository(em).hasActiveSiteUse(tenant, customer, site)).isTrue();
        var hql = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(hql.capture(), eq(UUID.class));
        assertThat(hql.getValue()).contains("o.tenantId=:tenant", "o.customerId=:customer",
                "o.status not in :terminal", "s.tenantId=o.tenantId", "s.orderId=o.id", "s.siteId=:site");
        verify(query).setParameter("tenant", tenant);
        verify(query).setParameter("customer", customer);
        verify(query).setParameter("site", site);
        verify(query).setParameter("terminal", Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        verify(query).setMaxResults(1);
    }
}
