package com.trailersaas.drivers.internal;

import java.util.*;
import static com.trailersaas.audit.AuditWriter.snapshot;

final class DriverAudit {
    static Map<String,Object> state(DriverEntity e) { return state(e,List.of()); }
    static Map<String,Object> state(DriverEntity e,List<String> redactedChanges) {
        // Account linkage and business name are relevant; raw license/medical values are deliberately excluded.
        return snapshot("name",e.name,"userId",e.userId,"active",e.active,"licenseNumberPresent",e.licenseNumber!=null,"medicalCertificatePresent",e.medicalCertificateExpires!=null,"redactedChanges",List.copyOf(redactedChanges),"version",e.version);
    }
}
