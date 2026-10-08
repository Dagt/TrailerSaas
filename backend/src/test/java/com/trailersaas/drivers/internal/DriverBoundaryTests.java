package com.trailersaas.drivers.internal;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class DriverBoundaryTests {
    @Test void repositoryReadsRequireExplicitTenantAndNeverOfferGenericLookup() {
        for(var method:DriverRepository.class.getDeclaredMethods()) {
            assertThat(method.getName()).isNotIn("findAll","findById");
            if(method.getName().equals("list") || method.getName().equals("resource")) {
                assertThat(method.getParameterTypes()[0]).isEqualTo(UUID.class);
            }
        }
        assertThat(DriverEntity.class.getDeclaredFields()).filteredOn(f->f.getName().equals("version"))
                .allMatch(f->f.isAnnotationPresent(jakarta.persistence.Version.class));
    }
    @Test void versionsAndRequestFieldsAreStrict() {
        var allowed=Set.of("expectedVersion");
        assertThat(DriverValidation.version(Map.of("expectedVersion",0),allowed)).isZero();
        for(Object value:java.util.List.of(-1,1.5,"0")) {
            assertThatThrownBy(()->DriverValidation.version(Map.of("expectedVersion",value),allowed)).isInstanceOf(DriverFailure.class);
        }
        assertThatThrownBy(()->DriverValidation.version(Map.of(),allowed)).isInstanceOf(DriverFailure.class);
        assertThatThrownBy(()->DriverValidation.fields(Map.of("unknown","secret"),allowed)).isInstanceOf(DriverFailure.class);
        assertThatThrownBy(()->DriverValidation.active(null)).isInstanceOf(DriverFailure.class);
    }
    @ParameterizedTest @ValueSource(strings={"23505","23503","23514"})
    void onlyKnownIntegritySqlStatesBecomeDomainConflicts(String state) {
        assertThat(DriverFailure.expectedIntegrity(new RuntimeException(new SQLException("private",state)))).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"08006","57014","P0001","XX000"})
    void infrastructureSqlStatesRemainFailures(String state) {
        assertThat(DriverFailure.expectedIntegrity(new RuntimeException(new SQLException("private",state)))).isFalse();
    }
    @Test void expiredMedicalDatesAndNullableLicenseAreValidMetadata() {
        assertThat(DriverValidation.date("2000-01-01")).isEqualTo(java.time.LocalDate.of(2000,1,1));
        assertThat(DriverValidation.date(null)).isNull();
        assertThat(DriverValidation.text(null,true)).isNull();
        assertThat(DriverValidation.text("  License ",true)).isEqualTo("  License ");
        assertThatThrownBy(()->DriverValidation.text("  ",false)).isInstanceOf(DriverFailure.class);
        assertThatThrownBy(()->DriverValidation.date("tomorrow")).isInstanceOf(DriverFailure.class);
        assertThatThrownBy(()->DriverValidation.userId("1-1-1-1-1")).isInstanceOf(DriverFailure.class);
    }
}
