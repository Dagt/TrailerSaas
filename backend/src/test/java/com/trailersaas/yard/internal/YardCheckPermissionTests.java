package com.trailersaas.yard.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.trailersaas.identity.*;
import com.trailersaas.notifications.MissingTrailerAlerts;
import com.trailersaas.trailers.TrailerMovementProjection;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class YardCheckPermissionTests {
  @ParameterizedTest @ValueSource(booleans={true,false}) void checkCapabilityCannotSubstituteForCorrection(boolean unknown) {
    var checks=mock(YardCheckService.class);var auth=mock(TenantAuthorization.class);var ledger=mock(MobileOperationLedger.class);var movement=mock(TrailerMovementProjection.class);var alerts=mock(MissingTrailerAlerts.class);
    var actor=mock(AuthenticatedUserPrincipal.class);var tenant=UUID.randomUUID();doThrow(new AccessDeniedException("Correction denied")).when(auth).requireTenantCapability(actor,tenant,Capability.TRAILER_LOCATION_CORRECT);
    var service=new YardCheckReconciliationService(checks,mock(YardActionService.class),mock(YardMoveService.class),mock(YardContextService.class),auth,ledger,movement,alerts,mock(JdbcTemplate.class));
    assertThatThrownBy(()->service.reconcile(actor,tenant,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID().toString(),Map.of(),unknown)).isInstanceOf(AccessDeniedException.class);
    verify(checks).authorize(actor,tenant);verifyNoInteractions(ledger,movement,alerts);
  }
}
