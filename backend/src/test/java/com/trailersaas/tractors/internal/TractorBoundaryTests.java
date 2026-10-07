package com.trailersaas.tractors.internal;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TractorBoundaryTests {
    @Test void repositoryReadsRequireExplicitTenantAndNeverOfferGenericLookup() {
        for(var method:TractorRepository.class.getDeclaredMethods()) {
            assertThat(method.getName()).isNotIn("findAll","findById");
            if(method.getName().equals("list") || method.getName().equals("resource")) {
                assertThat(method.getParameterTypes()[0]).isEqualTo(UUID.class);
            }
        }
        assertThat(TractorEntity.class.getDeclaredFields()).filteredOn(f->f.getName().equals("version"))
                .allMatch(f->f.isAnnotationPresent(jakarta.persistence.Version.class));
    }
    @Test void versionsAndRequestFieldsAreStrict() {
        var allowed=Set.of("expectedVersion");
        assertThat(TractorValidation.version(Map.of("expectedVersion",0),allowed)).isZero();
        for(Object value:java.util.List.of(-1,1.5,"0")) {
            assertThatThrownBy(()->TractorValidation.version(Map.of("expectedVersion",value),allowed)).isInstanceOf(TractorFailure.class);
        }
        assertThatThrownBy(()->TractorValidation.version(Map.of(),allowed)).isInstanceOf(TractorFailure.class);
        assertThatThrownBy(()->TractorValidation.fields(Map.of("unknown","secret"),allowed)).isInstanceOf(TractorFailure.class);
        assertThatThrownBy(()->TractorValidation.active(null)).isInstanceOf(TractorFailure.class);
    }
    @ParameterizedTest @ValueSource(strings={"23505","23503","23514"})
    void onlyKnownIntegritySqlStatesBecomeDomainConflicts(String state) {
        assertThat(TractorFailure.expectedIntegrity(new RuntimeException(new SQLException("private",state)))).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"08006","57014","P0001","XX000"})
    void infrastructureSqlStatesRemainFailures(String state) {
        assertThat(TractorFailure.expectedIntegrity(new RuntimeException(new SQLException("private",state)))).isFalse();
    }
    @Test void displayIsNeverRewrittenAndUnicodeIsPreserved() {
        for(String value:java.util.List.of(" t114 ","bh  t114","T-114","T0114","äéßı café","\u00a0")) {
            assertThat(TractorValidation.number(value)).isEqualTo(value);
        }
    }
    @ParameterizedTest @ValueSource(strings={"","   ","T\t1","T\n1","T\r1","T\u00001","T\u007f1","T\u00851","T\u009f1"})
    void controlsAndEmptyAsciiKeysAreRejected(String value) {
        assertThatThrownBy(()->TractorValidation.number(value)).isInstanceOf(TractorFailure.class);
    }
}

