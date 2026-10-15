package com.trailersaas.identity.internal;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.trailersaas.identity.Capability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCapabilitiesTests {
    @ParameterizedTest
    @MethodSource("expectedGrants")
    void exactConservativeMappingAllowsOnlyApprovedCapabilities(RoleCode role, String names) {
        Set<Capability> expected = Arrays.stream(names.split(" ")).map(Capability::valueOf).collect(Collectors.toSet());
        for (var capability : Capability.values()) {
            assertThat(RoleCapabilities.grants(role, capability)).as("%s / %s", role, capability)
                    .isEqualTo(expected.contains(capability));
        }
    }

    static Stream<Arguments> expectedGrants() {
        return Stream.of(
                Arguments.of(RoleCode.TENANT_ADMIN, """
                        DRIVER_VIEW TRACTOR_VIEW CUSTOMER_VIEW SITE_VIEW TENANT_SETTINGS_MANAGE USER_INVITE USER_DISABLE ROLE_ASSIGN AUDIT_VIEW CUSTOMER_CREATE CUSTOMER_EDIT SITE_CREATE SITE_EDIT DRIVER_CREATE DRIVER_EDIT TRACTOR_CREATE TRACTOR_EDIT TRAILER_CREATE TRAILER_EDIT TRAILER_CUSTOMER_REASSIGN TRAILER_OOS_MARK TRAILER_RETURN_TO_SERVICE ORDER_CREATE ORDER_EDIT ORDER_CANCEL ORDER_COMPLETE TRIP_PLAN TRIP_ASSIGN_DRIVER TRIP_ASSIGN_TRACTOR TRIP_ASSIGN_TRAILER TRIP_REROUTE TRAILER_SUBSTITUTE_AUTHORIZE TRAILER_LOCATION_CORRECT TRAILER_STATE_CORRECT TRAILER_VIEW_ALL ORDER_VIEW_ALL TRIP_VIEW_ALL DOCUMENT_VIEW_ALL ALERT_VIEW
                        """.strip()),
                Arguments.of(RoleCode.TRANSPORT_OPERATIONS, """
                        DRIVER_VIEW TRACTOR_VIEW CUSTOMER_VIEW SITE_VIEW ORDER_CREATE ORDER_EDIT ORDER_CANCEL ORDER_COMPLETE TRIP_PLAN TRIP_ASSIGN_DRIVER TRIP_ASSIGN_TRACTOR TRIP_ASSIGN_TRAILER TRIP_REROUTE TRAILER_SUBSTITUTE_AUTHORIZE TRAILER_VIEW_ALL ORDER_VIEW_ALL TRIP_VIEW_ALL DOCUMENT_VIEW_ALL ALERT_VIEW
                        """.strip()),
                Arguments.of(RoleCode.CUSTOMER_DISPATCHER, """
                        CUSTOMER_VIEW SITE_VIEW ORDER_CREATE ORDER_EDIT ORDER_CANCEL ORDER_COMPLETE TRIP_ASSIGN_DRIVER TRIP_ASSIGN_TRAILER TRIP_REROUTE TRAILER_SUBSTITUTE_AUTHORIZE TRAILER_VIEW_CUSTOMER_SCOPED ORDER_VIEW_CUSTOMER_SCOPED DOCUMENT_VIEW_CUSTOMER_SCOPED
                        """.strip()),
                Arguments.of(RoleCode.YARD_SPOTTER,
                        "YARD_MOVE_EXECUTE YARD_CHECK_EXECUTE TRAILER_LOCATION_CORRECT TRAILER_STATE_CORRECT"),
                Arguments.of(RoleCode.DRIVER, """
                        SHIFT_START SHIFT_END PRETRIP_CREATE POSTTRIP_CREATE TRIP_EXECUTE_ASSIGNED TRAILER_HOOK TRAILER_DROP ARRIVAL_CONFIRM DEPARTURE_CONFIRM POD_UPLOAD FUEL_EVENT_CREATE EXCEPTION_REPORT TRIP_VIEW_ASSIGNED
                        """.strip()));
    }

    @Test
    void absentInputsNeverGrantAndAssignmentCatalogIsClosed() {
        assertThat(RoleCapabilities.grants(null, Capability.ORDER_EDIT)).isFalse();
        assertThat(RoleCapabilities.grants(RoleCode.TENANT_ADMIN, null)).isFalse();
        assertThat(Arrays.stream(Capability.values()).filter(RoleCapabilities::requiresAssignment))
                .containsExactlyInAnyOrder(Capability.TRIP_VIEW_ASSIGNED, Capability.TRIP_EXECUTE_ASSIGNED);
        assertThat(Capability.values()).hasSize(59);
    }
}
