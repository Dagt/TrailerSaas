package com.trailersaas.customers.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Presence-aware PATCH parsing: omitted fields stay unchanged, explicit null clears nullable fields. */
final class MasterDataValidation {
    static final Set<String> LOCATION_TYPES = Set.of("BASE", "YARD", "DOCK", "CUSTOMER_AREA", "SHOP", "OTHER");
    private MasterDataValidation() {}

    static String text(Object value, String field, boolean nullable) {
        if (value == null && nullable) { return null; }
        if (!(value instanceof String s) || (!nullable && s.isBlank())) { throw MasterDataFailure.invalid(field); }
        return s;
    }
    static String choice(Object value, String field, Set<String> choices) {
        var s = text(value, field, false);
        if (!choices.contains(s)) { throw MasterDataFailure.invalid(field); }
        return s;
    }
    static String timezone(Object value) {
        var s = text(value, "timezone", false);
        if (!ZoneId.getAvailableZoneIds().contains(s)) { throw MasterDataFailure.invalid("timezone"); }
        return s;
    }
    static String country(Object value) {
        if (value == null) { return null; }
        if (!(value instanceof String s) || s.length() != 2) { throw MasterDataFailure.invalid("address.countryCode"); }
        return s;
    }
    static BigDecimal coordinate(Object value, String field, int max) {
        if (value == null) { return null; }
        if (!(value instanceof Number)) { throw MasterDataFailure.invalid(field); }
        BigDecimal number;
        try { number = new BigDecimal(value.toString()); }
        catch (NumberFormatException error) { throw MasterDataFailure.invalid(field); }
        if (number.abs().compareTo(BigDecimal.valueOf(max)) > 0) { throw MasterDataFailure.invalid(field); }
        return number.setScale(6, RoundingMode.HALF_UP);
    }
    static long integer(Object value, String field) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw MasterDataFailure.invalid(field);
        }
        return ((Number) value).longValue();
    }
    static Integer radius(Object value) {
        if (value == null) { return null; }
        long n = integer(value, "geofenceRadiusM");
        if (n <= 0 || n > Integer.MAX_VALUE) { throw MasterDataFailure.invalid("geofenceRadiusM"); }
        return (int) n;
    }
    static boolean bool(Object value) {
        if (!(value instanceof Boolean b)) { throw MasterDataFailure.invalid("active"); }
        return b;
    }
    static UUID customerId(Object value) {
        if (value == null) { return null; }
        if (!(value instanceof String s)) { throw MasterDataFailure.invalid("customerId"); }
        try {
            var id = UUID.fromString(s);
            if (!id.toString().equalsIgnoreCase(s)) { throw MasterDataFailure.invalid("customerId"); }
            return id;
        } catch (IllegalArgumentException error) { throw MasterDataFailure.invalid("customerId"); }
    }
    static long patch(Map<String, Object> fields, Set<String> allowed) {
        if (fields == null || fields.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
            // Never echo an untrusted key into the response.
            throw MasterDataFailure.invalid("body");
        }
        long version = integer(fields.get("expectedVersion"), "expectedVersion");
        if (version < 0) { throw MasterDataFailure.invalid("expectedVersion"); }
        return version;
    }
    static void address(PhysicalSiteEntity site, Object value) {
        if (value == null) {
            site.line1 = site.line2 = site.city = site.stateRegion = site.postalCode = site.countryCode = null;
            return;
        }
        if (!(value instanceof Map<?, ?> address)
                || address.keySet().stream().anyMatch(key -> !Set.of("line1", "line2", "city", "stateRegion", "postalCode", "countryCode").contains(key))) {
            throw MasterDataFailure.invalid("address");
        }
        if (address.containsKey("line1")) { site.line1 = nullableAddress(address.get("line1"), "line1"); }
        if (address.containsKey("line2")) { site.line2 = nullableAddress(address.get("line2"), "line2"); }
        if (address.containsKey("city")) { site.city = nullableAddress(address.get("city"), "city"); }
        if (address.containsKey("stateRegion")) { site.stateRegion = nullableAddress(address.get("stateRegion"), "stateRegion"); }
        if (address.containsKey("postalCode")) { site.postalCode = nullableAddress(address.get("postalCode"), "postalCode"); }
        if (address.containsKey("countryCode")) { site.countryCode = country(address.get("countryCode")); }
    }
    private static String nullableAddress(Object value, String field) {
        if (value == null) { return null; }
        if (!(value instanceof String s)) { throw MasterDataFailure.invalid("address." + field); }
        return s;
    }
}
