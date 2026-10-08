package com.trailersaas.identity.internal;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TenantSettingsValidationTests {
    @Test void patchRequiresIntegralNonnegativeVersionAndRejectsUnknownFields() {
        assertThat(TenantSettingsService.validatePatch(Map.of("expectedVersion", 0))).isZero();
        for (var input : java.util.List.of(Map.<String,Object>of(), Map.<String,Object>of("expectedVersion", -1),
                Map.<String,Object>of("expectedVersion", 1.0), Map.<String,Object>of("expectedVersion", 0, "retention", 30))) {
            assertThatThrownBy(() -> TenantSettingsService.validatePatch(input)).isInstanceOf(TenantSettingsFailure.class);
        }
    }
    @Test void thresholdOnlyConstrainedByPositivePostgresqlInteger() {
        assertThat(TenantSettingsService.threshold(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        for (Object value : new Object[]{0, -1, 1.5, "72", (long)Integer.MAX_VALUE + 1}) {
            assertThatThrownBy(() -> TenantSettingsService.threshold(value)).isInstanceOf(TenantSettingsFailure.class);
        }
    }
    @Test void nullableIanaTimezone() {
        assertThat(TenantSettingsService.timezone(null)).isNull();
        assertThat(TenantSettingsService.timezone("America/Chicago")).isEqualTo("America/Chicago");
        for (Object value : new Object[]{"", "Unknown/Zone", "+02:00", 3}) {
            assertThatThrownBy(() -> TenantSettingsService.timezone(value)).isInstanceOf(TenantSettingsFailure.class);
        }
    }
}
