package com.trailersaas.trips.internal;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class TripExecutionRequestTests {
  @Test void pendingInspectionPolicyNeverFabricatesEligibility(){assertThat(new PendingDeparturePrerequisites().satisfied(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID())).isFalse();}
  @Test void nullOccurrenceCannotAdvanceAnything(){for(var a:TripExecutionRequests.Action.values()) assertThatThrownBy(()->TripExecutionRequests.parse(a,Map.of())).isInstanceOf(TripFailure.class);}
}
