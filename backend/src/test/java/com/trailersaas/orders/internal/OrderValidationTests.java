package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class OrderValidationTests {
    Map<String, Object> valid() {
        return new HashMap<>(Map.of("customerId", UUID.randomUUID().toString(), "orderNumber", "ORD-123",
                "requestedStops", List.of(Map.of("sequenceNumber", 1, "stopType", "DELIVERY", "siteSnapshot", Map.of("name", "Receiving facility")))));
    }
    @Test void zeroCargoNullableNumbersOpenWeightVocabularyAndMultipleStops() {
        assertThat(OrderValidation.create(valid()).cargoLines()).isEmpty();
        var input = valid();
        input.put("cargoLines", List.of(Map.of("description", "Products", "quantity", new BigDecimal("1.25"), "weight", new BigDecimal("-2.5"), "weightUnit", "customer-specified")));
        input.put("requestedStops", List.of(Map.of("sequenceNumber", 2, "stopType", "DELIVERY", "siteSnapshot", Map.of("name", "Destination")),
                Map.of("sequenceNumber", 1, "stopType", "PICKUP", "siteSnapshot", Map.of("name", "Origin"))));
        var result = OrderValidation.create(input);
        assertThat(result.cargoLines().getFirst().quantity()).isEqualByComparingTo("1.25");
        assertThat(result.cargoLines().getFirst().weightUnit()).isEqualTo("customer-specified");
        assertThat(result.cargoLines().getFirst().palletCount()).isNull();
        assertThat(result.requestedStops()).hasSize(2);
    }
    @ParameterizedTest @ValueSource(strings = {"plannedTrailerId", "status", "tenantId", "createdByUserId", "version", "unknown"})
    void unsupportedOrMalformedCreateFieldsAreRejectedAndNotEchoed(String field) {
        var input = valid(); input.put(field, "private-value");
        assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class).hasMessage("The request contains invalid values.");
    }
    @Test void plannedTrailerIsOptionalNullableAndStrictlyUuid() {
        var input = valid(); assertThat(OrderValidation.create(input).plannedTrailerId()).isNull();
        input.put("plannedTrailerId", null); assertThat(OrderValidation.create(input).plannedTrailerId()).isNull();
        var trailer = UUID.randomUUID(); input.put("plannedTrailerId", trailer.toString());
        assertThat(OrderValidation.create(input).plannedTrailerId()).isEqualTo(trailer);
        input.put("plannedTrailerId", 42); assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"customerId", "orderNumber", "requestedStops"})
    void requiredFieldsCannotBeMissing(String field) {
        var input = valid(); input.remove(field); assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class);
    }
    @Test void duplicateSequenceEmptyStopsAndMissingDestinationRejected() {
        var input = valid(); var stop = ((List<?>) input.get("requestedStops")).getFirst();
        input.put("requestedStops", List.of(stop, stop));
        assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class);
        input.put("requestedStops", List.of()); assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class);
        input.put("requestedStops", List.of(Map.of("sequenceNumber", 1, "stopType", "DELIVERY")));
        assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"tenantId", "customerId", "siteId", "locationId", "roles", "capabilities"})
    void snapshotHasNoAuthorizationOrResourceReferenceFields(String field) {
        assertThatThrownBy(() -> OrderValidation.snapshot(Map.of("name", "Receiving", field, "private"))).isInstanceOf(OrderFailure.class);
    }
    @Test void snapshotIsIndependentImmutableAndCanCoexistWithMasterReferences() {
        var address = new HashMap<String, Object>(); address.put("line1", "710 Kimberly Dr"); address.put("line2", null);
        var raw = new HashMap<String, Object>(); raw.put("name", "Receiving"); raw.put("address", address); raw.put("timezone", "America/Chicago");
        var input = valid(); input.put("requestedStops", List.of(Map.of("sequenceNumber", 1, "siteId", UUID.randomUUID().toString(),
                "locationId", UUID.randomUUID().toString(), "siteSnapshot", raw, "stopType", "DELIVERY")));
        var snapshot = OrderValidation.create(input).requestedStops().getFirst().siteSnapshot();
        raw.put("name", "Changed"); address.put("line1", "Changed");
        assertThat(snapshot).containsEntry("name", "Receiving");
        assertThat(((Map<?, ?>) snapshot.get("address")).get("line1")).isEqualTo("710 Kimberly Dr");
        assertThatThrownBy(() -> snapshot.put("name", "Changed")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void snapshotsMustBeMeaningfulAndOptionalMetadataIsValidated() {
        for (var snapshot : List.of(Map.of(), Map.of("name", " "), Map.of("name", "Site", "timezone", "Unknown/Zone"),
                Map.of("name", "Site", "latitude", 91), Map.of("name", "Site", "longitude", -181),
                Map.of("name", "Site", "geofenceRadiusM", 0), Map.of("name", "Site", "address", Map.of("countryCode", "USA")))) {
            assertThatThrownBy(() -> OrderValidation.snapshot(snapshot)).isInstanceOf(OrderFailure.class);
        }
    }
    @Test void locationRequiresSiteEvenWithSnapshotAndMalformedNumbersDatesUuidsFail() {
        var input = valid(); input.put("requestedStops", List.of(Map.of("sequenceNumber", 1, "locationId", UUID.randomUUID().toString(), "siteSnapshot", Map.of("name", "Site"), "stopType", "DELIVERY")));
        assertThatThrownBy(() -> OrderValidation.create(input)).isInstanceOf(OrderFailure.class);
        assertThatThrownBy(() -> OrderValidation.uuid("1-1-1-1-1", "customerId", false)).isInstanceOf(OrderFailure.class);
        assertThatThrownBy(() -> OrderValidation.integer(1.5, "quantity", false)).isInstanceOf(OrderFailure.class);
        assertThatThrownBy(() -> OrderValidation.decimal(Double.NaN, "weight")).isInstanceOf(OrderFailure.class);
        assertThatThrownBy(() -> OrderValidation.instant("2026-10-08T14:00:00", "scheduledAt")).isInstanceOf(OrderFailure.class);
    }
    @Test void cursorRoundTripsAndLimitMatchesExistingContract() {
        var position = new OrderValidation.Position(Instant.parse("2026-10-08T12:00:00.123456Z"), UUID.randomUUID());
        assertThat(OrderValidation.Position.decode(position.encode())).isEqualTo(position);
        assertThat(OrderValidation.filters(Map.of()).limit()).isEqualTo(50);
        assertThat(OrderValidation.filters(Map.of("limit", "200")).limit()).isEqualTo(200);
        assertThatThrownBy(() -> OrderValidation.Position.decode(position.encode() + "=")).isInstanceOf(OrderFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"appointmentFrom", "appointmentTo", "unplanned", "active", "sort", "tenantId"})
    void deferredOrUnknownFiltersCannotBeSilentlyIgnored(String field) {
        assertThatThrownBy(() -> OrderValidation.filters(Map.of(field, "value"))).isInstanceOf(OrderFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "201", "-1", "1.5", "unknown"})
    void invalidLimitsFail(String limit) { assertThatThrownBy(() -> OrderValidation.filters(Map.of("limit", limit))).isInstanceOf(OrderFailure.class); }
    @ParameterizedTest @ValueSource(strings = {"23503", "23505", "23514", "08006", "57014", "P0001"})
    void infrastructureIsNotMistakenForIntegrity(String state) {
        var error = new org.springframework.dao.DataAccessResourceFailureException("private", new SQLException("private", state));
        assertThat(OrderFailure.expectedIntegrity(error)).isEqualTo(Set.of("23503", "23505", "23514").contains(state));
    }
}
