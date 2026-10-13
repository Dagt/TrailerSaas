package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trailersaas.identity.*;
import org.springframework.security.access.AccessDeniedException;
import com.trailersaas.trailers.TrailerMovementProjection;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class YardActionPermissionTests {
  @ParameterizedTest @ValueSource(strings={"LOCATION_CORRECTION","LOAD_STATE_CHANGE"})
  void yardAuthorityCannotSubstituteForSpecificCorrectiveCapability(String type) {
    var reads=mock(YardContextService.class);var auth=mock(TenantAuthorization.class);var ledger=mock(MobileOperationLedger.class);
    var actor=mock(AuthenticatedUserPrincipal.class);var tenant=UUID.randomUUID();
    var capability=type.equals("LOCATION_CORRECTION")?Capability.TRAILER_LOCATION_CORRECT:Capability.TRAILER_STATE_CORRECT;
    doThrow(new AccessDeniedException("Missing specific corrective capability")).when(auth).requireTenantCapability(actor,tenant,capability);
    var service=new YardActionService(reads,mock(YardMoveService.class),auth,ledger,mock(TrailerMovementProjection.class),mock(JdbcTemplate.class),JsonMapper.builder().build());
    assertThatThrownBy(()->service.execute(actor,tenant,UUID.randomUUID().toString(),type,Map.of())).isInstanceOf(AccessDeniedException.class);
    verify(reads).authorize(actor,tenant);verify(auth).requireTenantCapability(actor,tenant,capability);verifyNoInteractions(ledger);
  }
}
