package com.trailersaas.trailers.internal;

import java.util.*;
import static com.trailersaas.audit.AuditWriter.snapshot;

final class TrailerAudit {
    static Map<String,Object> state(TrailerEntity e) { return state(e,List.of()); }
    static Map<String,Object> state(TrailerEntity e,List<String> redactedChanges) {
        // Do not copy free-form dispatcher notes, physical projections, couplings or operational history.
        return snapshot("operationalNumber",e.operationalNumber,"designatedCustomerId",e.designatedCustomerId,"trailerType",e.trailerType,"usagePurpose",e.usagePurpose,"active",e.active,"capabilityNotesPresent",e.capabilityNotes!=null,"redactedChanges",List.copyOf(redactedChanges),"version",e.version);
    }
    static Map<String,Object> qr(TrailerQrEntity q) { return snapshot("trailerId",q.trailerId,"active",q.active,"issuedAt",q.issuedAt,"revokedAt",q.revokedAt); }
}
