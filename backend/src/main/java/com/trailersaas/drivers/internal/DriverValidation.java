package com.trailersaas.drivers.internal;

import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

final class DriverValidation {
    private DriverValidation() {}
    static void fields(Map<String,Object> fields, Set<String> allowed) {
        if (fields==null || fields.keySet().stream().anyMatch(key -> !allowed.contains(key))) { throw DriverFailure.invalid(); }
    }
    static long version(Map<String,Object> fields, Set<String> allowed) {
        fields(fields, allowed);
        Object value=fields.get("expectedVersion");
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                || ((Number)value).longValue()<0) { throw DriverFailure.invalid(); }
        return ((Number)value).longValue();
    }
    static String text(Object value, boolean nullable) {
        if (value==null && nullable) { return null; }
        if (!(value instanceof String s) || (!nullable && s.isBlank())) { throw DriverFailure.invalid(); }
        return s;
    }
    static boolean active(Object value) {
        if (!(value instanceof Boolean b)) { throw DriverFailure.invalid(); }
        return b;
    }
    static LocalDate date(Object value) {
        if (value==null) { return null; }
        if (!(value instanceof String s)) { throw DriverFailure.invalid(); }
        try { return LocalDate.parse(s); }
        catch (DateTimeParseException failure) { throw DriverFailure.invalid(); }
    }
    static UUID userId(Object value) {
        if (!(value instanceof String s)) { throw DriverFailure.invalid(); }
        try {
            UUID id=UUID.fromString(s);
            if (!id.toString().equalsIgnoreCase(s)) { throw DriverFailure.invalid(); }
            return id;
        } catch (IllegalArgumentException failure) { throw DriverFailure.invalid(); }
    }
}
