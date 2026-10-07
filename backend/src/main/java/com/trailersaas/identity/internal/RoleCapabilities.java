package com.trailersaas.identity.internal;

import java.util.Map;
import java.util.Set;

import com.trailersaas.identity.Capability;

import static com.trailersaas.identity.Capability.*;

final class RoleCapabilities {
    private static final Map<RoleCode, Set<Capability>> GRANTS = Map.of(
            RoleCode.TENANT_ADMIN, Set.of(
                    TENANT_SETTINGS_MANAGE, USER_INVITE, USER_DISABLE, ROLE_ASSIGN, AUDIT_VIEW,
                    CUSTOMER_CREATE, CUSTOMER_EDIT, SITE_CREATE, SITE_EDIT, CUSTOMER_VIEW, SITE_VIEW,
                    DRIVER_CREATE, DRIVER_EDIT, DRIVER_VIEW, TRACTOR_CREATE, TRACTOR_EDIT, TRACTOR_VIEW, TRAILER_CREATE, TRAILER_EDIT,
                    TRAILER_CUSTOMER_REASSIGN, TRAILER_OOS_MARK, TRAILER_RETURN_TO_SERVICE,
                    ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_COMPLETE, TRIP_PLAN, TRIP_ASSIGN_DRIVER,
                    TRIP_ASSIGN_TRACTOR, TRIP_ASSIGN_TRAILER, TRIP_REROUTE, TRAILER_SUBSTITUTE_AUTHORIZE,
                    TRAILER_LOCATION_CORRECT, TRAILER_STATE_CORRECT,
                    TRAILER_VIEW_ALL, ORDER_VIEW_ALL, TRIP_VIEW_ALL, DOCUMENT_VIEW_ALL, ALERT_VIEW),
            RoleCode.TRANSPORT_OPERATIONS, Set.of(
                    CUSTOMER_VIEW, SITE_VIEW, DRIVER_VIEW, TRACTOR_VIEW,
                    ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_COMPLETE, TRIP_PLAN, TRIP_ASSIGN_DRIVER,
                    TRIP_ASSIGN_TRACTOR, TRIP_ASSIGN_TRAILER, TRIP_REROUTE, TRAILER_SUBSTITUTE_AUTHORIZE,
                    TRAILER_VIEW_ALL, ORDER_VIEW_ALL, TRIP_VIEW_ALL, DOCUMENT_VIEW_ALL, ALERT_VIEW),
            RoleCode.CUSTOMER_DISPATCHER, Set.of(
                    CUSTOMER_VIEW, SITE_VIEW,
                    ORDER_CREATE, ORDER_EDIT, ORDER_CANCEL, ORDER_COMPLETE,
                    TRIP_ASSIGN_DRIVER, TRIP_ASSIGN_TRAILER, TRIP_REROUTE, TRAILER_SUBSTITUTE_AUTHORIZE,
                    TRAILER_VIEW_CUSTOMER_SCOPED, ORDER_VIEW_CUSTOMER_SCOPED, DOCUMENT_VIEW_CUSTOMER_SCOPED),
            RoleCode.YARD_SPOTTER, Set.of(
                    YARD_MOVE_EXECUTE, YARD_CHECK_EXECUTE, TRAILER_LOCATION_CORRECT, TRAILER_STATE_CORRECT),
            RoleCode.DRIVER, Set.of(
                    SHIFT_START, SHIFT_END, PRETRIP_CREATE, POSTTRIP_CREATE, TRIP_EXECUTE_ASSIGNED,
                    TRAILER_HOOK, TRAILER_DROP, ARRIVAL_CONFIRM, DEPARTURE_CONFIRM, POD_UPLOAD,
                    FUEL_EVENT_CREATE, EXCEPTION_REPORT, TRIP_VIEW_ASSIGNED));

    private RoleCapabilities() {}

    static boolean grants(RoleCode role, Capability capability) {
        return role != null && capability != null && GRANTS.getOrDefault(role, Set.of()).contains(capability);
    }

    static boolean requiresAssignment(Capability capability) {
        return capability == TRIP_VIEW_ASSIGNED || capability == TRIP_EXECUTE_ASSIGNED;
    }
}
