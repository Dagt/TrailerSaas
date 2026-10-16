package com.trailersaas.customers.internal;

import java.util.Map;
import static com.trailersaas.audit.AuditWriter.snapshot;

/** Explicit authorized business fields; never an entity/HTTP-body dump. */
final class MasterDataAudit {
    static Map<String,Object> state(CustomerEntity e) { return snapshot("name",e.name,"status",e.status,"version",e.version); }
    static Map<String,Object> state(PhysicalSiteEntity e) {
        return snapshot("name",e.name,"address",snapshot("line1",e.line1,"line2",e.line2,"city",e.city,"stateRegion",e.stateRegion,"postalCode",e.postalCode,"countryCode",e.countryCode),"timezone",e.timezone,"latitude",e.latitude,"longitude",e.longitude,"geofenceRadiusM",e.geofenceRadiusM,"active",e.active,"version",e.version);
    }
    static Map<String,Object> state(OperationalLocationEntity e) { return snapshot("siteId",e.siteId,"customerId",e.customerId,"name",e.name,"type",e.type,"code",e.code,"active",e.active,"version",e.version); }
}
