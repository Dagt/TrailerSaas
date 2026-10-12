package com.trailersaas.documents.internal;

import com.trailersaas.identity.*;
import com.trailersaas.drivers.DriverIdentityPolicy;
import java.util.UUID;
import org.springframework.stereotype.Service;
import static com.trailersaas.documents.internal.DocumentContracts.Type;

/** Upload permission follows the persisted type, never a read grant or client actor hint. */
@Service
class DocumentUploadAuthority {
  private final TenantAuthorization authorization;
  private final DriverIdentityPolicy drivers;
  DocumentUploadAuthority(TenantAuthorization authorization,DriverIdentityPolicy drivers) { this.authorization=authorization;this.drivers=drivers; }
  static Capability capability(Type type) {
    return switch(type) {
      case BOL,POD,DELIVERY_PHOTO -> Capability.POD_UPLOAD;
      case PRETRIP_FRONT,PRETRIP_REAR,PRETRIP_DRIVER_SIDE,PRETRIP_PASSENGER_SIDE -> Capability.PRETRIP_CREATE;
      case FUEL_RECEIPT -> Capability.FUEL_EVENT_CREATE;
      case DAMAGE_PHOTO -> Capability.EXCEPTION_REPORT;
    };
  }
  UUID require(AuthenticatedUserPrincipal actor,UUID tenant,Type type,boolean lock) {
    authorization.requireTenantCapability(actor,tenant,capability(type));
    return (lock?drivers.lockActiveForUser(tenant,actor.userId()):drivers.activeForUser(tenant,actor.userId()))
        .orElseThrow(()->new com.trailersaas.documents.EvidenceValidationException(com.trailersaas.documents.EvidenceValidationException.Code.CAPABILITY_DENIED));
  }
}
