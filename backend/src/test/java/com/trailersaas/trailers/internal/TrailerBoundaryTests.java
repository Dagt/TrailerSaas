package com.trailersaas.trailers.internal;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TrailerBoundaryTests {
    @Test void repositoriesHaveOnlyScopedOrExactTokenReadBoundaries() {
        for(var method:TrailerRepository.class.getDeclaredMethods()) {
            assertThat(method.getName()).isNotIn("findAll","findById");
            if(List.of("list","resource","qr","qrHistory").contains(method.getName())) {
                assertThat(method.getParameterTypes()[0]).isEqualTo(UUID.class);
            }
            if(method.getName().equals("activeQrForToken")) { assertThat(method.getParameterTypes()).containsExactly(String.class); }
        }
        assertThat(TrailerEntity.class.getDeclaredFields()).filteredOn(f->f.getName().equals("version"))
            .allMatch(f->f.isAnnotationPresent(jakarta.persistence.Version.class));
    }
    @Test void normalizationIsConservativeAndDisplayNeverChanges() {
        assertThat(TrailerValidation.normalized(" t114 ")).isEqualTo("T114");
        assertThat(TrailerValidation.normalized("bh  t114")).isEqualTo("BH T114");
        for(String value:List.of("T-114","T0114","äéßı","\u00a0")) {
            assertThat(TrailerValidation.normalized(value)).isEqualTo(value);
            assertThat(TrailerValidation.number(value)).isEqualTo(value);
        }
    }
    @ParameterizedTest @ValueSource(strings={"","   ","T\t1","T\n1","T\r1","T\u00001","T\u007f1","T\u00851","T\u009f1"})
    void controlsAndEmptyAsciiKeysAreRejected(String value) {
        assertThatThrownBy(()->TrailerValidation.number(value)).isInstanceOf(TrailerFailure.class);
    }
    @Test void strictVersionsAndTypedFiltersRejectUnknownOrInvalidInput() {
        for(Object value:List.of(-1,1.5,"0")) {
            assertThatThrownBy(()->TrailerValidation.version(Map.of("expectedVersion",value),Set.of("expectedVersion"))).isInstanceOf(TrailerFailure.class);
        }
        for(var fields:List.of(Map.<String,String>of("unknown","secret"),Map.of("active","yes"),Map.of("currentLocationId","bad"),
                Map.of("loadState","BAD"),Map.of("locationState","BAD"),Map.of("serviceabilityState","BAD"))) {
            assertThatThrownBy(()->TrailerValidation.filters(fields)).isInstanceOf(TrailerFailure.class);
        }
        assertThat(TrailerValidation.filters(Map.of("operationalNumber"," t114 ","active","false")))
            .containsEntry("operationalNumber","T114").containsEntry("active",false);
    }
    @Test void tokensAreCanonical128BitOpaqueIdentifiersAndDtoToStringRedactsThem() {
        var tokens=new HashSet<String>();
        for(int i=0;i<1000;i++) {
            String token=TrailerQrToken.generate();assertThat(token).matches("[A-Za-z0-9_-]{22}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(16);
            assertThat(TrailerQrToken.requireCanonical(token)).isEqualTo(token);assertThat(tokens.add(token)).isTrue();
            assertThat(new TrailerDtos.Qr(UUID.randomUUID(),UUID.randomUUID(),token,"T114",true,null,null).toString()).doesNotContain(token);
        }
    }
    @ParameterizedTest @ValueSource(strings={"synthetic-secret-qr-token","AAAAAAAAAAAAAAAAAAAAAB","AAAAAAAAAAAAAAAAAAAAAA=","AAAAAAAAAAAAAAAAAAAA","00000000-0000-0000-0000-000000000000"})
    void nonCanonicalTokensFailWithoutEcho(String token) {
        assertThatThrownBy(()->TrailerQrToken.requireCanonical(token)).isInstanceOf(TrailerFailure.class).hasMessageNotContaining(token);
    }
    @ParameterizedTest @ValueSource(strings={"23505","23503","23514"})
    void onlyExpectedIntegrityStatesAreConflicts(String state) { assertThat(TrailerFailure.expectedIntegrity(new RuntimeException(new SQLException("private",state)))).isTrue(); }
    @ParameterizedTest @ValueSource(strings={"08006","57014","P0001","XX000"})
    void infrastructureErrorsAreNotConflicts(String state) { assertThat(TrailerFailure.expectedIntegrity(new RuntimeException(new SQLException("private",state)))).isFalse(); }
}
