package com.trailersaas.jobs.internal;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RetryPolicyTests {
    @Test void exponentialBackoffUsesControllableTimeAndCapsAfterJitter() {
        var policy=new RetryPolicy(Duration.ofSeconds(30),Duration.ofMinutes(30),()->0.5);
        var clock=Clock.fixed(Instant.parse("2026-10-14T18:00:00Z"),ZoneOffset.UTC);
        assertThat(policy.nextAt(clock.instant(),1)).isEqualTo(clock.instant().plusSeconds(30));
        assertThat(policy.delay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.delay(5)).isEqualTo(Duration.ofMinutes(8));
        assertThat(policy.delay(20)).isEqualTo(Duration.ofMinutes(30));
        assertThat(new RetryPolicy(Duration.ofSeconds(30),Duration.ofMinutes(30),()->1).delay(20)).isEqualTo(Duration.ofMinutes(30));
        assertThat(new RetryPolicy(Duration.ofSeconds(30),Duration.ofMinutes(30),()->0).delay(1)).isEqualTo(Duration.ofSeconds(24));
    }
    @ParameterizedTest @ValueSource(ints={-1,0,21,Integer.MAX_VALUE})
    void invalidAttemptsCannotWrapBackoffOrRetryForever(int attempt) {
        var policy=new RetryPolicy(Duration.ofSeconds(30),Duration.ofMinutes(30),()->0.5);
        assertThatIllegalArgumentException().isThrownBy(()->policy.delay(attempt));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,-0.1,1.1})
    void nonFiniteOrOutOfRangeJitterIsRejected(double value) {
        assertThatIllegalArgumentException().isThrownBy(()->new RetryPolicy(Duration.ofSeconds(30),Duration.ofMinutes(30),()->value).delay(1));
    }
    @Test void enablingWorkerRequiresExplicitUniqueAuthorizedTenantListAndBoundedConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(()->new JobsProperties(true,List.of(),Duration.ofSeconds(5),4,4,Duration.ofSeconds(30),Duration.ofMinutes(2),5,Duration.ofSeconds(30),Duration.ofMinutes(30)));
        var t=UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(()->new JobsProperties(false,List.of(t,t),Duration.ofSeconds(5),4,4,Duration.ofSeconds(30),Duration.ofMinutes(2),5,Duration.ofSeconds(30),Duration.ofMinutes(30)));
        assertThatIllegalArgumentException().isThrownBy(()->new JobsProperties(false,List.of(t),Duration.ofMillis(1),4,4,Duration.ofSeconds(30),Duration.ofMinutes(2),5,Duration.ofSeconds(30),Duration.ofMinutes(30)));
        assertThatIllegalArgumentException().isThrownBy(()->new JobsProperties(false,List.of(t),Duration.ofSeconds(5),4,4,Duration.ofSeconds(30),Duration.ofSeconds(1),5,Duration.ofSeconds(30),Duration.ofMinutes(30)));
    }
}
