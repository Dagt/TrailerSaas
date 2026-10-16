package com.trailersaas.drivers.internal;

import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import com.trailersaas.audit.AuditWriter;
import static com.trailersaas.audit.AuditWriter.Action.*;
import java.util.Set;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.TenantAuthorization;
import com.trailersaas.identity.DriverAccountLinkPolicy;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.identity.Capability.*;
import static com.trailersaas.drivers.internal.DriverValidation.*;
import static com.trailersaas.drivers.internal.DriverDtos.*;

@Service
@Transactional
class DriverService {
    private final TenantAuthorization authorization;
    private final AuditWriter audit;
    private final DriverRepository repository;
    private final DriverAccountLinkPolicy linkPolicy;
    DriverService(TenantAuthorization authorization,DriverRepository repository,DriverAccountLinkPolicy linkPolicy,AuditWriter audit) {
        this.audit=audit;
        this.authorization=authorization; this.repository=repository; this.linkPolicy=linkPolicy;
    }
    Driver create(AuthenticatedUserPrincipal actor, UUID tenant, Map<String,Object> fields) {
        authorization.requireTenantCapability(actor,tenant,DRIVER_CREATE);
        fields(fields,Set.of("name","licenseNumber","medicalCertificateExpires"));
        var entity=new DriverEntity();
        entity.initialize(tenant,text(fields.get("name"),false));
        entity.licenseNumber=text(fields.get("licenseNumber"),true);
        entity.medicalCertificateExpires=date(fields.get("medicalCertificateExpires"));
        repository.insert(entity); flush();
        audit.human(tenant,actor.userId(),DRIVER_CREATED,entity.id,null,DriverAudit.state(entity));
        return Driver.of(entity);
    }
    @Transactional(readOnly=true)
    Items list(AuthenticatedUserPrincipal actor,UUID tenant) {
        authorization.requireTenantCapability(actor,tenant,DRIVER_VIEW);
        return new Items(repository.list(tenant).stream().map(Driver::of).toList());
    }
    @Transactional(readOnly=true)
    Driver get(AuthenticatedUserPrincipal actor,UUID tenant,UUID id) {
        authorization.requireTenantCapability(actor,tenant,DRIVER_VIEW);
        return Driver.of(repository.resource(tenant,id,false));
    }
    Driver patch(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,Object> fields) {
        authorization.requireTenantCapability(actor,tenant,DRIVER_EDIT);
        long expected=version(fields,Set.of("expectedVersion","name","licenseNumber","medicalCertificateExpires","active"));
        var entity=repository.resource(tenant,id,false);
        entity.requireVersion(expected);
        var before=DriverAudit.state(entity);var oldLicense=entity.licenseNumber;var oldMedical=entity.medicalCertificateExpires;
        if(fields.containsKey("name")) { entity.name=text(fields.get("name"),false); }
        if(fields.containsKey("licenseNumber")) { entity.licenseNumber=text(fields.get("licenseNumber"),true); }
        if(fields.containsKey("medicalCertificateExpires")) { entity.medicalCertificateExpires=date(fields.get("medicalCertificateExpires")); }
        if(fields.containsKey("active")) { entity.active=active(fields.get("active")); }
        flush();
        var redacted=new ArrayList<String>();
        if(!Objects.equals(oldLicense,entity.licenseNumber))redacted.add("licenseNumber");
        if(!Objects.equals(oldMedical,entity.medicalCertificateExpires))redacted.add("medicalCertificateExpires");
        audit.human(tenant,actor.userId(),DRIVER_UPDATED,entity.id,before,DriverAudit.state(entity,redacted));
        return Driver.of(entity);
    }
    Driver link(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,Object> fields) {
        authorization.requireTenantCapability(actor,tenant,DRIVER_EDIT);
        long expected=version(fields,Set.of("expectedVersion","userId"));
        UUID target=userId(fields.get("userId"));
        var entity=repository.resource(tenant,id,true);
        entity.requireVersion(expected);
        var before=DriverAudit.state(entity);
        linkPolicy.requireEligible(tenant,target);
        if(entity.userId!=null) { throw DriverFailure.conflict(); }
        entity.userId=target;
        flush();
        audit.human(tenant,actor.userId(),DRIVER_USER_LINKED,entity.id,before,DriverAudit.state(entity));
        return Driver.of(entity);
    }
    void unlink(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,Object> fields) {
        authorization.requireTenantCapability(actor,tenant,DRIVER_EDIT);
        long expected=version(fields,Set.of("expectedVersion"));
        var entity=repository.resource(tenant,id,false);
        entity.requireVersion(expected);
        var before=DriverAudit.state(entity);
        entity.userId=null; // Dirty checking makes an already-unlinked command a version-preserving no-op.
        flush();
        audit.human(tenant,actor.userId(),DRIVER_USER_UNLINKED,entity.id,before,DriverAudit.state(entity));
    }

    private void flush() {
        try { repository.flush(); }
        catch(DataIntegrityViolationException | ConstraintViolationException failure) {
            if(!DriverFailure.expectedIntegrity(failure)) { throw failure; }
            throw DriverFailure.conflict();
        }
    }
}
