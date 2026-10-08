package com.trailersaas.tractors.internal;

import java.util.Map;
import java.util.Set;


final class TractorValidation {
    private TractorValidation() {}
    static void fields(Map<String,Object> fields, Set<String> allowed) {
        if (fields==null || fields.keySet().stream().anyMatch(key -> !allowed.contains(key))) { throw TractorFailure.invalid(); }
    }
    static long version(Map<String,Object> fields, Set<String> allowed) {
        fields(fields, allowed);
        Object value=fields.get("expectedVersion");
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
                || ((Number)value).longValue()<0) { throw TractorFailure.invalid(); }
        return ((Number)value).longValue();
    }
    static String text(Object value, boolean nullable) {
        if (value==null && nullable) { return null; }
        if (!(value instanceof String s) || (!nullable && s.isBlank())) { throw TractorFailure.invalid(); }
        return s;
    }
    static boolean active(Object value) {
        if (!(value instanceof Boolean b)) { throw TractorFailure.invalid(); }
        return b;
    }
    static String number(Object value) {
        if (!(value instanceof String s) || s.isEmpty() || s.codePoints().anyMatch(Character::isISOControl)
                || s.chars().allMatch(c -> c==' ')) { throw TractorFailure.invalid(); }
        // Preserve display exactly; the database alone generates the canonical key.
        return s;
    }
}
