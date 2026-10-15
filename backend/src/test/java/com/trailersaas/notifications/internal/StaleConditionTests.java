package com.trailersaas.notifications.internal;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StaleConditionTests {
 final Instant at=Instant.parse("2026-10-15T12:00:00Z");
 @Test void inclusivePhysicalBoundaryHasNoAdministrativeOrLocationFallback(){
  assertThat(StaleCondition.evaluate(true,at.minus(Duration.ofHours(72)),true,at,72)).isEqualTo(StaleCondition.Result.STALE);
  assertThat(StaleCondition.evaluate(true,at.minus(Duration.ofHours(72)).plusNanos(1),true,at,72)).isEqualTo(StaleCondition.Result.FRESH);
  assertThat(StaleCondition.evaluate(true,at.minus(Duration.ofHours(72)).minusNanos(1),true,at,72)).isEqualTo(StaleCondition.Result.STALE);
  assertThat(StaleCondition.evaluate(true,at.minus(Duration.ofHours(80)),true,at,96)).isEqualTo(StaleCondition.Result.FRESH);
 }
 @Test void absentFutureInfiniteOrInactiveNeverProduceStale(){
  assertThat(StaleCondition.evaluate(true,null,true,at,72)).isEqualTo(StaleCondition.Result.NO_OBSERVATION);
  assertThat(StaleCondition.evaluate(true,at.plusSeconds(1),true,at,72)).isEqualTo(StaleCondition.Result.FUTURE);
  assertThat(StaleCondition.evaluate(true,at.minusSeconds(1),false,at,72)).isEqualTo(StaleCondition.Result.INVALID_TIMESTAMP);
  assertThat(StaleCondition.evaluate(false,at.minus(Duration.ofDays(100)),true,at,72)).isEqualTo(StaleCondition.Result.INACTIVE);
 }
 @Test void acceptedIntegerMaximumThresholdDoesNotOverflow(){
  assertThat(StaleCondition.evaluate(true,Instant.parse("0001-01-01T00:00:00Z"),true,at,Integer.MAX_VALUE)).isEqualTo(StaleCondition.Result.FRESH);
  assertThatThrownBy(()->StaleCondition.evaluate(true,at,true,at,0)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void deploymentDefaultsAndInvalidScopeFailClosed(){
  var settings=new StaleDetectorProperties(false,List.of(),Duration.ofSeconds(30),Duration.ofHours(1),100);
  assertThat(new StaleDetectorConfiguration().staleDeploymentAuthorization(settings,t->false).permits(UUID.randomUUID())).isFalse();
  assertThatThrownBy(()->new StaleDetectorProperties(true,List.of(),Duration.ofSeconds(30),Duration.ofHours(1),100)).isInstanceOf(IllegalArgumentException.class);
  var enabled=new StaleDetectorProperties(true,List.of(UUID.randomUUID()),Duration.ofSeconds(30),Duration.ofHours(1),100);
  assertThatThrownBy(()->new StaleDetectorConfiguration().staleDeploymentAuthorization(enabled,t->false)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new StaleDetectorProperties(false,List.of(),Duration.ofMillis(1),Duration.ofHours(1),501)).isInstanceOf(IllegalArgumentException.class);
 }
}
