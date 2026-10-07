package com.trailersaas.identity.internal;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;

class InvitationIntegrityTests {
    @ParameterizedTest @ValueSource(strings = {"23505", "23503", "23514"})
    void onlyUniqueForeignKeyAndCheckViolationsAreExpected(String state) {
        assertThat(InvitationIntegrity.isExpectedRejection(failure(state))).isTrue();
    }

    @ParameterizedTest @NullSource
    @ValueSource(strings = {"08001", "08006", "57014", "P0001", "23502", "42501", "40001", "40P01"})
    void infrastructureUnknownAndOtherSqlFailuresRemainServerFailures(String state) {
        assertThat(InvitationIntegrity.isExpectedRejection(failure(state))).isFalse();
    }

    @Test void exceptionTypeAloneWithoutSqlStateDoesNotProveDomainConflict() {
        assertThat(InvitationIntegrity.isExpectedRejection(new DataIntegrityViolationException("unknown cause"))).isFalse();
    }

    private DataIntegrityViolationException failure(String state) {
        return new DataIntegrityViolationException("safe wrapper",
                new IllegalStateException(new SQLException("synthetic private DB detail", state)));
    }
}
