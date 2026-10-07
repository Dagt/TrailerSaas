package com.trailersaas.customers.internal;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.*;

class MasterDataExceptionHandlerTests {
    final MasterDataExceptionHandler handler = new MasterDataExceptionHandler();

    @Test
    void expectedFailuresHaveStableCodesAndSafeFieldExtensions() {
        assertProblem(handler.failure(MasterDataFailure.invalid("name")), 400, "VALIDATION_FAILED");
        assertThat(handler.failure(MasterDataFailure.invalid("name")).getProperties()).containsKey("errors");
        assertProblem(handler.failure(MasterDataFailure.missing()), 404, "RESOURCE_NOT_FOUND");
        assertProblem(handler.failure(MasterDataFailure.conflict()), 409, "RESOURCE_CONFLICT");
        assertProblem(handler.failure(MasterDataFailure.stale()), 409, "STALE_VERSION");
    }

    @Test
    void databaseDetailsAndOptimisticLockMessagesAreNeverReturned() {
        var secret = "synthetic-secret SQL password foreign-customer-constraint";
        assertProblem(handler.conflict(new DataIntegrityViolationException(secret)), 409, "RESOURCE_CONFLICT");
        assertProblem(handler.stale(new OptimisticLockException(secret)), 409, "STALE_VERSION");
        assertProblem(handler.stale(new OptimisticLockingFailureException(secret)), 409, "STALE_VERSION");
        assertProblem(handler.invalid(new IllegalArgumentException(secret)), 400, "VALIDATION_FAILED");
    }

    private void assertProblem(ProblemDetail body, int status, String code) {
        assertThat(body.getStatus()).isEqualTo(status);
        assertThat(body.getProperties()).containsEntry("code", code);
        assertThat(body.getType().toString()).startsWith("https://api.trailersaas.example/problems/");
        assertThat(body.toString()).doesNotContain("synthetic-secret", "SQL", "password", "foreign-customer");
    }
}
