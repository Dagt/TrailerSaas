package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class TripBoundaryTests {
  @Test
  void repositoryDoesNotExposeGenericUnscopedReadsOrWrites() {
    assertThat(Modifier.isPublic(TripRepository.class.getModifiers())).isFalse();
    var names =
        Set.of(
            "resource",
            "list",
            "stops",
            "mappings",
            "tripsForOrder",
            "releaseMappings",
            "history",
            "mappedCustomers");
    var methods =
        Arrays.stream(TripRepository.class.getDeclaredMethods())
            .filter(m -> names.contains(m.getName()))
            .toList();
    assertThat(methods).hasSize(8);
    methods.forEach(m -> assertThat(m.getParameterTypes()[0]).isEqualTo(UUID.class));
    assertThat(Arrays.stream(TripRepository.class.getDeclaredMethods()).map(m -> m.getName()))
        .doesNotContain("findAll", "findById");
  }

  @Test
  void publicProjectionDoesNotExposeTenantRequestedStopHistoryOrJpaEntities() {
    for (var dto :
        List.of(
            TripContracts.Summary.class, TripContracts.Detail.class, TripContracts.Stop.class)) {
      assertThat(Arrays.stream(dto.getRecordComponents()).map(c -> c.getName()))
          .doesNotContain("tenantId", "orderStopId", "timeline");
    }
    assertThat(
            Arrays.stream(TripContracts.Detail.class.getRecordComponents()).map(c -> c.getName()))
        .contains("assignmentHistory");
    assertThat(
            Arrays.stream(TripContracts.Assignment.class.getRecordComponents())
                .map(c -> c.getName()))
        .doesNotContain("tenantId", "actorMembershipId");
  }
}
