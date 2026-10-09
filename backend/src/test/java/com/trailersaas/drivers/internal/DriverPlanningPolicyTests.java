package com.trailersaas.drivers.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.trailersaas.drivers.DriverPlanningPolicy;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DriverPlanningPolicyTests {
  @ParameterizedTest
  @ValueSource(strings = {"active", "inactive", "missing"})
  @SuppressWarnings("unchecked")
  void eligibilityIsFreshTenantBoundAndHeldWithSharedParentLock(String mode) {
    var em = mock(EntityManager.class);
    TypedQuery<DriverEntity> q = mock(TypedQuery.class, RETURNS_SELF);
    when(em.createQuery(anyString(), eq(DriverEntity.class))).thenReturn(q);
    var row = new DriverEntity();
    row.active = mode.equals("active");
    when(q.getResultStream())
        .thenReturn(
            mode.equals("missing")
                ? java.util.stream.Stream.empty()
                : java.util.stream.Stream.of(row));
    var tenant = UUID.randomUUID();
    var id = UUID.randomUUID();
    var policy = new DatabaseDriverPlanningPolicy(em);
    assertThat(policy.check(tenant, id))
        .isEqualTo(
            mode.equals("active")
                ? DriverPlanningPolicy.Eligibility.ELIGIBLE
                : mode.equals("inactive")
                    ? DriverPlanningPolicy.Eligibility.INACTIVE
                    : DriverPlanningPolicy.Eligibility.NOT_FOUND);
    verify(q).setParameter("tenant", tenant);
    verify(q).setParameter("id", id);
    verify(q).setLockMode(LockModeType.PESSIMISTIC_READ);
  }
}
