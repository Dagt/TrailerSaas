package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import static com.trailersaas.orders.internal.OrderContracts.*;

final class OrderValidation {
    private OrderValidation() {}
    static Create create(Map<String, Object> input) {
        fields(input, Set.of("customerId", "orderNumber", "poNumber", "shipmentNumber", "bolReference", "cargoLines", "requestedStops", "plannedTrailerId"), "body");
        var customer = uuid(input.get("customerId"), "customerId", false);
        var cargo = new ArrayList<CargoInput>();
        if (input.containsKey("cargoLines")) {
            for (var item : array(input.get("cargoLines"), "cargoLines")) {
                var c = object(item, "cargoLines");
                fields(c, Set.of("description", "quantity", "palletCount", "weight", "weightUnit"), "cargoLines");
                cargo.add(new CargoInput(text(c.get("description"), "cargoLines.description", false),
                        decimal(c.get("quantity"), "cargoLines.quantity"), integer(c.get("palletCount"), "cargoLines.palletCount", true),
                        decimal(c.get("weight"), "cargoLines.weight"), text(c.get("weightUnit"), "cargoLines.weightUnit", true)));
            }
        }
        var stops = new ArrayList<StopInput>();
        var sequences = new HashSet<Integer>();
        for (var item : array(input.get("requestedStops"), "requestedStops")) {
            var s = object(item, "requestedStops");
            fields(s, Set.of("sequenceNumber", "siteId", "locationId", "siteSnapshot", "stopType", "scheduledAt", "instructions"), "requestedStops");
            int sequence = integer(s.get("sequenceNumber"), "requestedStops.sequenceNumber", false);
            if (sequence < 1 || !sequences.add(sequence)) { throw OrderFailure.invalid("requestedStops.sequenceNumber"); }
            var site = uuid(s.get("siteId"), "requestedStops.siteId", true);
            var location = uuid(s.get("locationId"), "requestedStops.locationId", true);
            var snapshot = snapshot(s.get("siteSnapshot"));
            if ((location != null && site == null) || (site == null && snapshot == null)) { throw OrderFailure.invalid("requestedStops"); }
            stops.add(new StopInput(sequence, site, location, snapshot, text(s.get("stopType"), "requestedStops.stopType", false),
                    instant(s.get("scheduledAt"), "requestedStops.scheduledAt"), text(s.get("instructions"), "requestedStops.instructions", true)));
        }
        if (stops.isEmpty()) { throw OrderFailure.invalid("requestedStops"); }
        return new Create(customer, text(input.get("orderNumber"), "orderNumber", false), text(input.get("poNumber"), "poNumber", true),
                text(input.get("shipmentNumber"), "shipmentNumber", true), text(input.get("bolReference"), "bolReference", true),
                List.copyOf(cargo), List.copyOf(stops), uuid(input.get("plannedTrailerId"), "plannedTrailerId", true));
    }
    static void fields(Map<?, ?> input, Set<String> allowed, String field) {
        if (input == null || input.keySet().stream().anyMatch(key -> !allowed.contains(key))) { throw OrderFailure.invalid(field); }
    }
    static String text(Object input, String field, boolean nullable) {
        if (input == null && nullable) { return null; }
        if (!(input instanceof String s) || (!nullable && s.isBlank())) { throw OrderFailure.invalid(field); }
        return s;
    }
    static UUID uuid(Object input, String field, boolean nullable) {
        if (input == null && nullable) { return null; }
        var value = text(input, field, false);
        try { var id = UUID.fromString(value); if (!id.toString().equalsIgnoreCase(value)) { throw OrderFailure.invalid(field); } return id; }
        catch (IllegalArgumentException e) { throw OrderFailure.invalid(field); }
    }
    static BigDecimal decimal(Object input, String field) {
        if (input == null) { return null; }
        if (!(input instanceof Number)) { throw OrderFailure.invalid(field); }
        try { return new BigDecimal(input.toString()); } catch (NumberFormatException e) { throw OrderFailure.invalid(field); }
    }
    static Integer integer(Object input, String field, boolean nullable) {
        if (input == null && nullable) { return null; }
        if (!(input instanceof Byte || input instanceof Short || input instanceof Integer || input instanceof Long)) { throw OrderFailure.invalid(field); }
        long value = ((Number) input).longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) { throw OrderFailure.invalid(field); }
        return (int) value;
    }
    static Instant instant(Object input, String field) {
        if (input == null) { return null; }
        try { return Instant.parse(text(input, field, false)); } catch (java.time.DateTimeException e) { throw OrderFailure.invalid(field); }
    }
    private static List<?> array(Object input, String field) {
        if (!(input instanceof List<?> list)) { throw OrderFailure.invalid(field); } return list;
    }
    private static Map<String, Object> object(Object input, String field) {
        if (!(input instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) { throw OrderFailure.invalid(field); }
        var result = new LinkedHashMap<String, Object>(); map.forEach((k, v) -> result.put((String) k, v)); return result;
    }
    static Map<String, Object> snapshot(Object input) {
        if (input == null) { return null; }
        var s = object(input, "requestedStops.siteSnapshot");
        fields(s, Set.of("name", "address", "timezone", "latitude", "longitude", "geofenceRadiusM"), "requestedStops.siteSnapshot");
        text(s.get("name"), "requestedStops.siteSnapshot.name", false);
        if (s.get("address") != null) {
            var address = object(s.get("address"), "requestedStops.siteSnapshot.address");
            fields(address, Set.of("line1", "line2", "city", "stateRegion", "postalCode", "countryCode"), "requestedStops.siteSnapshot.address");
            address.forEach((k, v) -> text(v, "requestedStops.siteSnapshot.address", true));
            var country = (String) address.get("countryCode");
            if (country != null && country.length() != 2) { throw OrderFailure.invalid("requestedStops.siteSnapshot.address"); }
            s.put("address", Collections.unmodifiableMap(address));
        }
        if (s.get("timezone") != null && !ZoneId.getAvailableZoneIds().contains(text(s.get("timezone"), "requestedStops.siteSnapshot.timezone", false))) { throw OrderFailure.invalid("requestedStops.siteSnapshot.timezone"); }
        for (var field : List.of("latitude", "longitude")) {
            var value = decimal(s.get(field), "requestedStops.siteSnapshot." + field);
            if (value != null && value.abs().compareTo(BigDecimal.valueOf(field.equals("latitude") ? 90 : 180)) > 0) { throw OrderFailure.invalid("requestedStops.siteSnapshot." + field); }
        }
        var radius = integer(s.get("geofenceRadiusM"), "requestedStops.siteSnapshot.geofenceRadiusM", true);
        if (radius != null && radius < 1) { throw OrderFailure.invalid("requestedStops.siteSnapshot.geofenceRadiusM"); }
        return Collections.unmodifiableMap(s);
    }
    record Position(Instant createdAt, UUID id) {
        String encode() { return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8)); }
        static Position decode(String input) {
            try {
                if (input.length() > 160) { throw OrderFailure.invalid("cursor"); }
                var raw = new String(Base64.getUrlDecoder().decode(input), StandardCharsets.UTF_8).split("\\|", -1);
                if (raw.length != 2) { throw OrderFailure.invalid("cursor"); }
                var value = new Position(Instant.parse(raw[0]), uuid(raw[1], "cursor", false));
                if (!value.encode().equals(input)) { throw OrderFailure.invalid("cursor"); }
                return value;
            } catch (IllegalArgumentException | java.time.DateTimeException e) { throw OrderFailure.invalid("cursor"); }
        }
    }
    record Filters(UUID customerId, OrderStatus status, Position cursor, int limit, Boolean unplanned) {
        Filters(UUID customerId, OrderStatus status, Position cursor, int limit) { this(customerId,status,cursor,limit,null); }
    }
    private static Boolean unplanned(Map<String,String> input) {
        if (!input.containsKey("unplanned")) { return null; }
        if (!Set.of("true","false").contains(input.get("unplanned"))) { throw OrderFailure.invalid("unplanned"); }
        return Boolean.valueOf(input.get("unplanned"));
    }
    static Filters filters(Map<String, String> input) {
        fields(input, Set.of("customerId", "status", "cursor", "limit", "unplanned"), "query");
        OrderStatus status = null;
        if (input.containsKey("status")) {
            try { status = OrderStatus.valueOf(input.get("status")); } catch (IllegalArgumentException e) { throw OrderFailure.invalid("status"); }
        }
        int limit = 50;
        if (input.containsKey("limit")) {
            try { limit = Integer.parseInt(input.get("limit")); } catch (NumberFormatException e) { throw OrderFailure.invalid("limit"); }
        }
        // Existing OpenAPI Limit contract: 1..200, default 50.
        if (limit < 1 || limit > 200) { throw OrderFailure.invalid("limit"); }
        return new Filters(input.containsKey("customerId") ? uuid(input.get("customerId"), "customerId", false) : null,
                status, input.containsKey("cursor") ? Position.decode(input.get("cursor")) : null, limit, unplanned(input));
    }
}
