package com.trailersaas.trailers.internal;

import java.util.*;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerPlanningPolicy.Eligibility;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrailerPlanningPolicyTests {
    final EntityManager em = mock(EntityManager.class);
    final TrailerAccessPolicy access = mock(TrailerAccessPolicy.class);
    final UUID tenant = UUID.randomUUID(), trailerId = UUID.randomUUID(), customer = UUID.randomUUID();
    final AuthenticatedUserPrincipal actor = new AuthenticatedUserPrincipal(UUID.randomUUID(), "verified", null, null);
    @SuppressWarnings("unchecked")
    TypedQuery<TrailerEntity> query(TrailerEntity trailer, CustomerAccessScope scope) {
        TypedQuery<TrailerEntity> q = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(TrailerEntity.class))).thenReturn(q);
        when(q.getResultStream()).thenAnswer(invocation -> trailer == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(trailer));
        when(access.readScope(actor, tenant)).thenReturn(scope); return q;
    }
    TrailerEntity trailer() { var t = new TrailerEntity(); t.designatedCustomerId = customer; return t; }
    @Test void freshScopeAndSharedTenantBoundLockProtectNewPlanningEligibility() {
        var t = trailer(); var q = query(t, new CustomerAccessScope(false, Set.of(customer)));
        assertThat(new DatabaseTrailerPlanningPolicy(em, access).check(actor, tenant, trailerId)).isEqualTo(Eligibility.ELIGIBLE);
        verify(access).readScope(actor, tenant); verify(q).setParameter("tenant", tenant); verify(q).setParameter("id", trailerId);
        verify(q).setLockMode(LockModeType.PESSIMISTIC_READ);
        var hql = ArgumentCaptor.forClass(String.class); verify(em).createQuery(hql.capture(), eq(TrailerEntity.class));
        assertThat(hql.getValue()).contains("t.tenantId=:tenant", "t.id=:id");
        verify(em, never()).persist(any()); verify(em, never()).flush();
        assertThat(t.active).isTrue(); assertThat(t.serviceabilityState).isEqualTo("IN_SERVICE"); assertThat(t.version).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"inactive", "oos", "unassigned", "foreignCustomer", "missing"})
    void unsafeTrailerResultsAreGenericAndNeverChangeState(String mode) {
        var t = trailer();
        if (mode.equals("inactive")) { t.active = false; }
        if (mode.equals("oos")) { t.serviceabilityState = "OUT_OF_SERVICE"; }
        if (mode.equals("unassigned")) { t.designatedCustomerId = null; }
        if (mode.equals("foreignCustomer")) { t.designatedCustomerId = UUID.randomUUID(); }
        query(mode.equals("missing") ? null : t, new CustomerAccessScope(false, Set.of(customer)));
        var expected = switch (mode) {
            case "inactive", "oos" -> Eligibility.UNAVAILABLE;
            case "missing" -> Eligibility.NOT_FOUND;
            default -> Eligibility.CUSTOMER_SCOPE_DENIED;
        };
        assertThat(new DatabaseTrailerPlanningPolicy(em, access).check(actor, tenant, trailerId)).isEqualTo(expected);
        assertThat(t.version).isZero(); assertThat(t.loadState).isEqualTo("UNKNOWN"); assertThat(t.locationState).isEqualTo("LOCATION_UNKNOWN");
    }
}
