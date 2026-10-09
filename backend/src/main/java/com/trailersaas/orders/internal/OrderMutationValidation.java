package com.trailersaas.orders.internal;

import java.util.Map;
import java.util.Set;
import com.trailersaas.orders.OrderLifecycleEvent.Resolution;

final class OrderMutationValidation {
    private OrderMutationValidation() {}
    static long version(Map<String, Object> input, Set<String> fields) {
        OrderValidation.fields(input, fields, "body");
        var value = input.get("expectedVersion");
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() < 0) { throw OrderFailure.invalid("expectedVersion"); }
        return ((Number) value).longValue();
    }
    static Resolution resolution(Object input) {
        if (input == null) { return null; }
        if (!(input instanceof Map<?, ?> map)) { throw OrderFailure.invalid("resolution"); }
        OrderValidation.fields(map, Set.of("type", "destinationSiteId", "instructions"), "resolution");
        var instructions = OrderValidation.text(map.get("instructions"), "resolution.instructions", true);
        if (instructions != null && instructions.isBlank()) { throw OrderFailure.invalid("resolution.instructions"); }
        return new Resolution(OrderValidation.text(map.get("type"), "resolution.type", false),
                OrderValidation.uuid(map.get("destinationSiteId"), "resolution.destinationSiteId", true), instructions);
    }
}
