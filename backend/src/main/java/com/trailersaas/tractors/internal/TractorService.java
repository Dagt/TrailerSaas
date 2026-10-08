package com.trailersaas.tractors.internal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.TenantAuthorization;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.identity.Capability.*;
import static com.trailersaas.tractors.internal.TractorValidation.*;
import static com.trailersaas.tractors.internal.TractorDtos.*;

@Service
@Transactional
class TractorService {
    private final TenantAuthorization authorization;
    private final TractorRepository repository;

    TractorService(TenantAuthorization authorization,TractorRepository repository) {
        this.authorization=authorization; this.repository=repository;
    }
    Tractor create(AuthenticatedUserPrincipal actor, UUID tenant, Map<String,Object> fields) {
        authorization.requireTenantCapability(actor,tenant,TRACTOR_CREATE);
        fields(fields,Set.of("operationalNumber"));
        var entity=new TractorEntity();
        entity.initialize(tenant,number(fields.get("operationalNumber")));

        repository.insert(entity); flush();
        return Tractor.of(entity);
    }
    @Transactional(readOnly=true)
    Items list(AuthenticatedUserPrincipal actor,UUID tenant) {
        authorization.requireTenantCapability(actor,tenant,TRACTOR_VIEW);
        return new Items(repository.list(tenant).stream().map(Tractor::of).toList());
    }
    @Transactional(readOnly=true)
    Tractor get(AuthenticatedUserPrincipal actor,UUID tenant,UUID id) {
        authorization.requireTenantCapability(actor,tenant,TRACTOR_VIEW);
        return Tractor.of(repository.resource(tenant,id));
    }
    Tractor patch(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,Object> fields) {
        authorization.requireTenantCapability(actor,tenant,TRACTOR_EDIT);
        long expected=version(fields,Set.of("expectedVersion","operationalNumber","active"));
        var entity=repository.resource(tenant,id);
        entity.requireVersion(expected);
        if(fields.containsKey("operationalNumber")) { entity.operationalNumber=number(fields.get("operationalNumber")); }
        if(fields.containsKey("active")) { entity.active=active(fields.get("active")); }
        flush();
        return Tractor.of(entity);
    }

    private void flush() {
        try { repository.flush(); }
        catch(DataIntegrityViolationException | ConstraintViolationException failure) {
            if(!TractorFailure.expectedIntegrity(failure)) { throw failure; }
            throw TractorFailure.conflict();
        }
    }
}
