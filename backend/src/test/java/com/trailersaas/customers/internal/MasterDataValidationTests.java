package com.trailersaas.customers.internal;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.trailersaas.customers.internal.MasterDataValidation.*;
import static org.assertj.core.api.Assertions.*;

class MasterDataValidationTests {
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    void nameMustNotBeBlank(String value) {
        invalid(() -> text(value, "name", false));
    }
    @ParameterizedTest
    @ValueSource(strings = {"America/Chicago", "America/Mexico_City", "UTC"})
    void acceptsIanaZones(String value) { assertThat(timezone(value)).isEqualTo(value); }
    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "+03:00", "", "America/Unknown"})
    void rejectsInvalidZonesWithoutEchoingValues(String value) { invalid(() -> timezone(value)); }
    @Test
    void optionalCoordinatesDoNotRequirePairAndHaveSchemaPrecision() {
        assertThat(coordinate(null, "longitude", 180)).isNull();
        assertThat(coordinate(new BigDecimal("90"), "latitude", 90)).isEqualByComparingTo("90");
        assertThat(coordinate(new BigDecimal("-180"), "longitude", 180)).isEqualByComparingTo("-180");
        assertThat(coordinate(new BigDecimal("41.1234567"), "latitude", 90)).isEqualByComparingTo("41.123457");
        invalid(() -> coordinate(new BigDecimal("90.00001"), "latitude", 90));
        invalid(() -> coordinate(-181, "longitude", 180));
        invalid(() -> coordinate("41", "latitude", 90));
    }
    @Test
    void radiusAndCountryAreOptionalAndValidated() {
        assertThat(radius(null)).isNull(); assertThat(radius(250)).isEqualTo(250);
        invalid(() -> radius(0)); invalid(() -> radius(-1)); invalid(() -> radius(1.5));
        invalid(() -> radius(Long.MAX_VALUE));
        assertThat(country(null)).isNull(); assertThat(country("US")).isEqualTo("US");
        invalid(() -> country("USA")); invalid(() -> country("U")); invalid(() -> country(12));
    }
    @Test
    void patchRequiresIntegralNonnegativeExpectedVersionAndRejectsUnknownFields() {
        var allowed = Set.of("expectedVersion", "name");
        assertThat(patch(Map.of("expectedVersion", 0), allowed)).isZero();
        invalid(() -> patch(Map.of(), allowed));
        invalid(() -> patch(Map.of("expectedVersion", -1), allowed));
        invalid(() -> patch(Map.of("expectedVersion", 1.5), allowed));
        invalid(() -> patch(Map.of("expectedVersion", "0"), allowed));
        invalid(() -> patch(Map.of("expectedVersion", 0, "synthetic-secret-field", 1), allowed));
    }
    @Test
    void patchAddressPreservesOmittedFieldsAndClearsExplicitNull() {
        var site = new PhysicalSiteEntity(); site.line1 = "Street"; site.city = "City"; site.countryCode = "US";
        var changes = new HashMap<String, Object>(); changes.put("line1", null);
        address(site, changes);
        assertThat(site.line1).isNull(); assertThat(site.city).isEqualTo("City");
        address(site, null);
        assertThat(site.city).isNull(); assertThat(site.countryCode).isNull();
        invalid(() -> address(site, Map.of("unknown", "value")));
    }
    @Test
    void locationCatalogNullableCustomerAndActiveAreClosed() {
        assertThat(LOCATION_TYPES).containsExactlyInAnyOrder("BASE", "YARD", "DOCK", "CUSTOMER_AREA", "SHOP", "OTHER");
        invalid(() -> choice("LOCATION_UNKNOWN", "type", LOCATION_TYPES));
        assertThat(customerId(null)).isNull();
        var id = UUID.randomUUID(); assertThat(customerId(id.toString())).isEqualTo(id);
        invalid(() -> customerId("1-1-1-1-1")); invalid(() -> bool(null)); invalid(() -> bool("true"));
    }
    @Test
    void responsesAreRecordsNotJpaEntitiesAndAggregatesCarryVersion() {
        for (var type : new Class<?>[]{MasterDataDtos.Customer.class, MasterDataDtos.Site.class, MasterDataDtos.Location.class}) {
            assertThat(type.isRecord()).isTrue(); assertThat(type.getAnnotation(Entity.class)).isNull();
            assertThat(Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                    .contains("id", "version", "createdAt", "updatedAt");
        }
        assertThat(Arrays.stream(VersionedMasterData.class.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Version.class)))
                .hasSize(1);
        var entity = new CustomerEntity(); entity.version = 2;
        assertThatThrownBy(() -> entity.requireVersion(1)).isInstanceOfSatisfying(MasterDataFailure.class,
                error -> assertThat(error.code).isEqualTo("STALE_VERSION"));
    }
    private void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(MasterDataFailure.class,
                error -> assertThat(error.code).isEqualTo("VALIDATION_FAILED"));
    }
}
