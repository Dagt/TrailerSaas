package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class TripExecutionRequestTests {
  @Test
  void geofenceRequiresFiniteStrictEvidenceAndClosedSource() {
    var body = new LinkedHashMap<String,Object>();
    body.put("occurredAt","2026-10-10T10:00:00Z");body.put("source","GEOFENCE_CONFIRMED");
    assertThatThrownBy(() -> TripExecutionRequests.parse(TripExecutionRequests.Action.ARRIVAL,body)).isInstanceOf(TripFailure.class);
    for (var invalid : List.of(-1.0,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)) {
      body.put("coordinates",Map.of("latitude",41,"longitude",-88,"accuracyM",invalid));
      assertThatThrownBy(() -> TripExecutionRequests.parse(TripExecutionRequests.Action.ARRIVAL,body)).isInstanceOf(TripFailure.class);
    }
    body.put("coordinates",Map.of("latitude",41,"longitude",-88,"accuracyM",25));
    assertThat(TripExecutionRequests.parse(TripExecutionRequests.Action.ARRIVAL,body).source()).isEqualTo("GEOFENCE_CONFIRMED");
    body.put("coordinates",Map.of("latitude",41,"longitude",-88,"accuracyM",25,"actor_type","SYSTEM"));
    assertThatThrownBy(() -> TripExecutionRequests.parse(TripExecutionRequests.Action.ARRIVAL,body)).isInstanceOf(TripFailure.class);
  }

  @Test
  void geodesicDistanceIsStableAtDatelinePolesAndAntipodes() {
    assertThat(GeofenceEvidence.distance(41,-88,41,-88)).isZero();
    assertThat(GeofenceEvidence.distance(0,179.999,0,-179.999)).isCloseTo(222.39,within(0.01));
    assertThat(GeofenceEvidence.distance(90,0,90,90)).isLessThan(0.001);
    assertThat(GeofenceEvidence.distance(0,0,0,180)).isCloseTo(20015114.44,within(0.1));
  }
  @Test
  void pendingInspectionPolicyNeverFabricatesEligibility() {
    var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var tenant=UUID.randomUUID();var driver=UUID.randomUUID();var tractor=UUID.randomUUID();var trip=UUID.randomUUID();
    org.mockito.Mockito.when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(Boolean.class),org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(false);
    var real=new com.trailersaas.inspections.internal.DatabaseTripDeparturePrerequisites(jdbc);
    assertThat(real.satisfied(tenant,driver,tractor,trip)).isFalse();
    org.mockito.Mockito.verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("doc.upload_status='UPLOADED'"),org.mockito.ArgumentMatchers.eq(Boolean.class),org.mockito.ArgumentMatchers.eq(trip),org.mockito.ArgumentMatchers.eq(tenant),org.mockito.ArgumentMatchers.eq(driver),org.mockito.ArgumentMatchers.any(java.sql.Timestamp.class),org.mockito.ArgumentMatchers.eq(tractor),org.mockito.ArgumentMatchers.any(java.sql.Timestamp.class));
  }

  @Test
  void nullOccurrenceCannotAdvanceAnything() {
    for (var a : TripExecutionRequests.Action.values())
      assertThatThrownBy(() -> TripExecutionRequests.parse(a, Map.of()))
          .isInstanceOf(TripFailure.class);
  }

  @Test
  void departureRequiresAnExplicitNextStop() {
    for (var action :
        List.of(
            TripExecutionRequests.Action.FIRST_DEPARTURE, TripExecutionRequests.Action.DEPARTURE))
      assertThatThrownBy(
              () ->
                  TripExecutionRequests.parse(action, Map.of("occurredAt", "2026-10-10T10:00:00Z")))
          .isInstanceOf(TripFailure.class);
  }

  @Test
  void coordinateOrderAndEquivalentNumericEncodingHaveOneLogicalPayload() {
    var a = new LinkedHashMap<String, Object>();
    a.put("latitude", 1);
    a.put("longitude", 2);
    a.put("accuracyM", 3);
    var b = new LinkedHashMap<String, Object>();
    b.put("accuracyM", 3.0);
    b.put("longitude", 2.0);
    b.put("latitude", 1.0);
    var x =
        TripExecutionRequests.parse(
            TripExecutionRequests.Action.ARRIVAL,
            Map.of("occurredAt", "2026-10-10T10:00:00Z", "source", "MANUAL", "coordinates", a));
    var y =
        TripExecutionRequests.parse(
            TripExecutionRequests.Action.ARRIVAL,
            Map.of("occurredAt", "2026-10-10T10:00:00Z", "source", "MANUAL", "coordinates", b));
    assertThat(x).isEqualTo(y);
    assertThat(x.coordinates().keySet()).containsExactly("latitude", "longitude", "accuracyM");
  }
}
