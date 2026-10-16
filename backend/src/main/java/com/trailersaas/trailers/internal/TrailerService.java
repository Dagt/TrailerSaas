package com.trailersaas.trailers.internal;
import java.time.Instant;
import com.trailersaas.audit.AuditWriter;
import static com.trailersaas.audit.AuditWriter.Action.*;
import java.util.List;
import java.util.Objects;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.trailersaas.identity.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.identity.Capability.*;
import static com.trailersaas.trailers.internal.TrailerDtos.*;
import static com.trailersaas.trailers.internal.TrailerValidation.*;

@Service @Transactional
class TrailerService {
    private final TenantAuthorization authorization;
    private final AuditWriter audit;
    private final TrailerAccessPolicy access;
    private final TrailerRepository repository;
    private final TrailerQrLookupContext qrContext;
    private final TenantContext tenantContext;
    TrailerService(TenantAuthorization authorization,TrailerAccessPolicy access,TrailerRepository repository,
            TrailerQrLookupContext qrContext,TenantContext tenantContext,AuditWriter audit) {
        this.audit=audit;
        this.authorization=authorization;this.access=access;this.repository=repository;this.qrContext=qrContext;this.tenantContext=tenantContext;
    }
    Trailer create(AuthenticatedUserPrincipal actor,UUID tenant,Map<String,Object> input) {
        authorization.requireTenantCapability(actor,tenant,TRAILER_CREATE);
        fields(input,Set.of("operationalNumber","designatedCustomerId","trailerType","capabilityNotes","usagePurpose"));
        var entity=new TrailerEntity(); entity.initialize(tenant,number(input.get("operationalNumber")));
        entity.designatedCustomerId=uuid(input.get("designatedCustomerId"));
        entity.trailerType=text(input.get("trailerType")); entity.capabilityNotes=text(input.get("capabilityNotes"));
        entity.usagePurpose=catalog(input.get("usagePurpose"),PURPOSES,true);
        repository.insert(entity); flush();
        audit.human(tenant,actor.userId(),TRAILER_CREATED,entity.id,null,TrailerAudit.state(entity));
        return Trailer.of(entity);
    }
    @Transactional(readOnly=true)
    Items list(AuthenticatedUserPrincipal actor,UUID tenant,Map<String,String> input) {
        var scope=access.readScope(actor,tenant); var filters=filters(input);
        var customer=(UUID)filters.get("designatedCustomerId");
        if(customer!=null && !scope.tenantWide() && !scope.customerIds().contains(customer)) { throw TrailerFailure.scope(); }
        return new Items(repository.list(tenant,scope,filters).stream().map(Trailer::of).toList());
    }
    @Transactional(readOnly=true)
    Trailer get(AuthenticatedUserPrincipal actor,UUID tenant,UUID id) {
        var scope=access.readScope(actor,tenant); var trailer=repository.resource(tenant,id,false);
        if(!scope.tenantWide() && (trailer.designatedCustomerId==null || !scope.customerIds().contains(trailer.designatedCustomerId))) { throw TrailerFailure.missing(); }
        return Trailer.of(trailer);
    }
    Trailer patch(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,Object> input) {
        authorization.requireTenantCapability(actor,tenant,TRAILER_EDIT);
        long expected=version(input,Set.of("expectedVersion","operationalNumber","trailerType","capabilityNotes","usagePurpose","active"));
        var entity=repository.resource(tenant,id,false); entity.requireVersion(expected);
        var before=TrailerAudit.state(entity);var oldNotes=entity.capabilityNotes;
        if(input.containsKey("operationalNumber")) { entity.operationalNumber=number(input.get("operationalNumber")); }
        if(input.containsKey("trailerType")) { entity.trailerType=text(input.get("trailerType")); }
        if(input.containsKey("capabilityNotes")) { entity.capabilityNotes=text(input.get("capabilityNotes")); }
        if(input.containsKey("usagePurpose")) { entity.usagePurpose=catalog(input.get("usagePurpose"),PURPOSES,true); }
        if(input.containsKey("active")) { entity.active=active(input.get("active")); }
        flush();
        audit.human(tenant,actor.userId(),TRAILER_UPDATED,entity.id,before,TrailerAudit.state(entity,Objects.equals(oldNotes,entity.capabilityNotes)?List.of():List.of("capabilityNotes")));
        return Trailer.of(entity);
    }
    @Transactional(readOnly=true)
    QrItems qrList(AuthenticatedUserPrincipal actor,UUID tenant,UUID id) {
        authorization.requireTenantCapability(actor,tenant,TRAILER_EDIT);
        var trailer=repository.resource(tenant,id,false);
        return new QrItems(repository.qrHistory(tenant,id).stream().map(q->Qr.of(q,trailer)).toList());
    }
    Qr issue(AuthenticatedUserPrincipal actor,UUID tenant,UUID id) {
        authorization.requireTenantCapability(actor,tenant,TRAILER_EDIT);
        var trailer=repository.resource(tenant,id,true);
        if(repository.qrHistory(tenant,id).stream().anyMatch(q->q.active)) { throw TrailerFailure.conflict(); }
        var qr=new TrailerQrEntity(); qr.id=UUID.randomUUID(); qr.tenantId=tenant; qr.trailerId=id;
        qr.publicToken=TrailerQrToken.generate(); qr.issuedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
        repository.insert(qr); flush();
        audit.human(tenant,actor.userId(),TRAILER_QR_ISSUED,qr.id,null,TrailerAudit.qr(qr));
        return Qr.of(qr,trailer);
    }
    void revoke(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,UUID qrId) {
        authorization.requireTenantCapability(actor,tenant,TRAILER_EDIT);
        repository.resource(tenant,id,true); var qr=repository.qr(tenant,id,qrId);
        if(!qr.active) { return; }
        var before=TrailerAudit.qr(qr);
        qr.active=false; qr.revokedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
        flush();
        audit.human(tenant,actor.userId(),TRAILER_QR_REVOKED,qr.id,before,TrailerAudit.qr(qr));
    }
    @Transactional(readOnly=true)
    Scan resolve(AuthenticatedUserPrincipal actor,String token) {
        TrailerQrToken.requireCanonical(token); qrContext.establish(token);
        var qr=repository.activeQrForToken(token);
        tenantContext.establishTenant(qr.tenantId);
        var trailer=repository.resource(qr.tenantId,qr.trailerId,false);
        if(!trailer.active) { throw TrailerFailure.missing(); }
        if(!access.mayResolveQr(actor,qr.tenantId,trailer.designatedCustomerId)) { throw TrailerFailure.missing(); }
        return Scan.of(trailer);
    }
    private void flush() {
        try { repository.flush(); }
        catch(DataIntegrityViolationException | ConstraintViolationException failure) {
            if(!TrailerFailure.expectedIntegrity(failure)) { throw failure; }
            throw TrailerFailure.conflict();
        }
    }
}
