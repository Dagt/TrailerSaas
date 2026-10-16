package com.trailersaas.audit.internal;

import com.trailersaas.audit.AuditWriter.*;
import java.math.*;
import java.time.Instant;
import java.util.*;

/** Closed per-action projections. No arbitrary bean, document, credential or free metadata serialization. */
final class AuditCatalog {
    static Set<String> keys(Action action) {
        if (action==Action.CUSTOMER_SITE_ASSOCIATED || action==Action.CUSTOMER_SITE_DISASSOCIATED)
            return Set.of("siteId","associationId","associated");
        return switch(action.entityType()) {
            case TENANT_SETTINGS -> Set.of("staleTrailerThresholdHours","defaultTimezone","version");
            case CUSTOMER -> Set.of("name","status","version");
            case PHYSICAL_SITE -> Set.of("name","address","timezone","latitude","longitude","geofenceRadiusM","active","version");
            case OPERATIONAL_LOCATION -> Set.of("siteId","customerId","name","type","code","active","version");
            case DRIVER -> Set.of("name","userId","active","licenseNumberPresent","medicalCertificatePresent","redactedChanges","version");
            case TRACTOR -> Set.of("operationalNumber","active","version");
            case TRAILER -> Set.of("operationalNumber","designatedCustomerId","trailerType","usagePurpose","active","capabilityNotesPresent","redactedChanges","version");
            case TRAILER_QR_IDENTITY -> Set.of("trailerId","active","issuedAt","revokedAt");
            case INVITATION -> Set.of("status","expiresAt","roles","membershipId");
            case TENANT_MEMBERSHIP -> Set.of("userId","status","roles");
        };
    }
    static Map<String,Object> safe(Action action,Map<String,Object> input) {
        if(input==null)return null;
        if(input.isEmpty() || !keys(action).containsAll(input.keySet()))throw invalid();
        var result=new TreeMap<String,Object>();
        input.forEach((key,value)->{
            Object normalized;
            if(key.equals("address") && value!=null) normalized=object(value,Set.of("line1","line2","city","stateRegion","postalCode","countryCode"));
            else if(key.equals("roles") && value!=null) {
                if(!(value instanceof List<?> list))throw invalid();
                var roles=new ArrayList<Map<String,Object>>();
                for(var role:list){var r=object(role,Set.of("roleCode","scopeType","customerId"));
                    if(!r.keySet().equals(Set.of("roleCode","scopeType","customerId")) || !(r.get("roleCode") instanceof String) || !Set.of("TENANT_ADMIN","TRANSPORT_OPERATIONS","CUSTOMER_DISPATCHER","YARD_SPOTTER","DRIVER").contains(r.get("roleCode")))throw invalid();
                    boolean scoped="CUSTOMER_DISPATCHER".equals(r.get("roleCode"));
                    if(!(scoped?"CUSTOMER":"TENANT").equals(r.get("scopeType")) || (r.get("customerId")!=null)!=scoped)throw invalid();
                    if(scoped)canonicalUuid(r.get("customerId"));roles.add(r);
                }
                normalized=roles.stream().sorted(Comparator.comparing(Object::toString)).toList();
            } else if(key.equals("redactedChanges")) {
                var allowed=action.entityType()==EntityType.DRIVER?Set.of("licenseNumber","medicalCertificateExpires"):Set.of("capabilityNotes");
                if(!(value instanceof List<?> list) || !allowed.containsAll(list))throw invalid();
                normalized=list.stream().map(Object::toString).distinct().sorted().toList();
            } else normalized=scalar(value);
            if(key.endsWith("Id") && normalized!=null)canonicalUuid(normalized);
            result.put(key,normalized);
        });
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> object(Object value,Set<String> keys) {
        if(!(value instanceof Map<?,?> map) || !keys.containsAll(map.keySet()))throw invalid();
        var result=new TreeMap<String,Object>();map.forEach((k,v)->result.put((String)k,scalar(v)));return Collections.unmodifiableMap(result);
    }
    private static Object scalar(Object value) {
        if(value==null || value instanceof String || value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger)return value;
        if(value instanceof UUID || value instanceof Instant)return value.toString();
        if(value instanceof BigDecimal n)return n.stripTrailingZeros();
        if(value instanceof Double n && Double.isFinite(n))return n;
        if(value instanceof Float n && Float.isFinite(n))return n;
        throw invalid();
    }
    private static void canonicalUuid(Object value) {
        try{if(!(value instanceof String s) || !UUID.fromString(s).toString().equals(s))throw invalid();}
        catch(IllegalArgumentException e){throw invalid();}
    }
    static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid audit projection or attribution");}
}
