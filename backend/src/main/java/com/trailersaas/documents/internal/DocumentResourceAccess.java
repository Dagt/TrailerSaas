package com.trailersaas.documents.internal;

import com.trailersaas.identity.*;
import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.trips.DocumentStopDirectory;
import com.trailersaas.trips.EvidenceWorkDirectory;
import com.trailersaas.trailers.TrailerEvidenceDirectory;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.trailersaas.documents.internal.DocumentContracts.*;

/** Shared all-or-nothing read check for transport, evidence validation and ledger replay. */
@Service
class DocumentResourceAccess {
  private final DriverIdentityPolicy drivers;
  private final DocumentStopDirectory stops;
  private final EvidenceWorkDirectory work;
  private final TrailerEvidenceDirectory trailers;
  private final org.springframework.beans.factory.ObjectProvider<com.trailersaas.documents.InspectionDocumentDirectory> inspections;
  DocumentResourceAccess(DriverIdentityPolicy d,DocumentStopDirectory s,EvidenceWorkDirectory w,TrailerEvidenceDirectory t,org.springframework.beans.factory.ObjectProvider<com.trailersaas.documents.InspectionDocumentDirectory> i) { drivers=d;stops=s;work=w;trailers=t;inspections=i; }
  List<Link> visible(AuthenticatedUserPrincipal actor,UUID tenant,List<Link> linked,DocumentAccessPolicy.ReadScope scope) {
    UUID driver=scope.assignedWork()?drivers.activeForUser(tenant,actor.userId()).orElse(null):null;
    var visible=linked.stream().filter(link->switch(link.entityType()) {
      case TRIP_STOP -> stops.find(tenant,link.entityId(),false).map(s->scope.tenantWide()
          || (!s.customerIds().isEmpty() && scope.customerIds().containsAll(s.customerIds()))
          || (driver!=null && driver.equals(s.driverId()))).orElse(false);
      // No Customer-scoped Trip read capability exists; inspection/fuel Trip attachments are not Customer grants.
      case TRIP -> work.readTrip(tenant,link.entityId()).map(t->scope.tenantWide() || (driver!=null && driver.equals(t.driverId()))).orElse(false);
      case TRAILER -> scope.tenantWide() && trailers.exists(tenant,link.entityId());
      case INSPECTION -> inspections.getIfAvailable()!=null && inspections.getObject().find(tenant,link.entityId()).map(i->scope.tenantWide() || driver!=null && driver.equals(i.driverId())).orElse(false);
    }).toList();
    return visible.size()==linked.size()?visible:List.of();
  }
}
