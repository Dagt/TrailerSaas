package com.trailersaas.customers.internal;

import java.time.Instant;
import com.trailersaas.audit.AuditWriter;
import static com.trailersaas.audit.AuditWriter.snapshot;
import static com.trailersaas.audit.AuditWriter.Action.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.identity.CustomerAccessScope;
import com.trailersaas.identity.TenantAuthorization;
import com.trailersaas.customers.CustomerSiteUsageGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.trailersaas.identity.Capability.*;
import static com.trailersaas.customers.internal.MasterDataDtos.*;
import static com.trailersaas.customers.internal.MasterDataValidation.*;

@Service
@Transactional
class MasterDataService {
    private final TenantAuthorization authorization;
    private final AuditWriter audit;
    private final MasterDataRepository repository;
    private final List<CustomerSiteUsageGuard> siteUsageGuards;
    MasterDataService(TenantAuthorization authorization, MasterDataRepository repository,
            List<CustomerSiteUsageGuard> siteUsageGuards, AuditWriter audit) {
        this.audit = audit;
        this.authorization = authorization; this.repository = repository;
        this.siteUsageGuards = List.copyOf(siteUsageGuards);
    }

    Customer createCustomer(AuthenticatedUserPrincipal user, UUID tenant, CreateCustomer request) {
        authorization.requireTenantCapability(user, tenant, CUSTOMER_CREATE);
        var entity = new CustomerEntity();
        entity.initialize(tenant, text(request.name(), "name", false));
        repository.insert(entity); repository.flush();
        audit.human(tenant,user.userId(),CUSTOMER_CREATED,entity.id,null,MasterDataAudit.state(entity));
        return Customer.of(entity);
    }

    @Transactional(readOnly = true)
    Items<Customer> customers(AuthenticatedUserPrincipal user, UUID tenant) {
        var scope = authorization.resolveCustomerScope(user, tenant, CUSTOMER_VIEW);
        if (!scope.tenantWide()) { repository.requireActiveTenant(tenant); }
        return new Items<>(repository.customers(tenant, scope).stream().map(Customer::of).toList());
    }

    @Transactional(readOnly = true)
    Customer customer(AuthenticatedUserPrincipal user, UUID tenant, UUID id) {
        authorization.requireCustomerCapability(user, tenant, id, CUSTOMER_VIEW);
        if (!authorization.resolveCustomerScope(user, tenant, CUSTOMER_VIEW).tenantWide()) { repository.requireActiveTenant(tenant); }
        return Customer.of(repository.resource(CustomerEntity.class, tenant, id));
    }

    Customer updateCustomer(AuthenticatedUserPrincipal user, UUID tenant, UUID id, Map<String, Object> fields) {
        authorization.requireTenantCapability(user, tenant, CUSTOMER_EDIT);
        long expected = patch(fields, Set.of("expectedVersion", "name", "status"));
        var entity = repository.resource(CustomerEntity.class, tenant, id);
        entity.requireVersion(expected);
        var before = MasterDataAudit.state(entity);
        if (fields.containsKey("name")) { entity.name = text(fields.get("name"), "name", false); }
        if (fields.containsKey("status")) { entity.status = choice(fields.get("status"), "status", Set.of("ACTIVE", "INACTIVE")); }
        repository.flush();
        audit.human(tenant,user.userId(),CUSTOMER_UPDATED,entity.id,before,MasterDataAudit.state(entity));
        return Customer.of(entity);
    }

    Site createSite(AuthenticatedUserPrincipal user, UUID tenant, CreateSite request) {
        authorization.requireTenantCapability(user, tenant, SITE_CREATE);
        var entity = new PhysicalSiteEntity();
        entity.initialize(tenant, text(request.name(), "name", false));
        entity.timezone = timezone(request.timezone());
        entity.latitude = coordinate(request.latitude(), "latitude", 90);
        entity.longitude = coordinate(request.longitude(), "longitude", 180);
        entity.geofenceRadiusM = radius(request.geofenceRadiusM());
        if (request.address() != null) {
            var a = request.address();
            entity.line1 = a.line1(); entity.line2 = a.line2(); entity.city = a.city();
            entity.stateRegion = a.stateRegion(); entity.postalCode = a.postalCode();
            entity.countryCode = country(a.countryCode());
        }
        repository.insert(entity); repository.flush();
        audit.human(tenant,user.userId(),SITE_CREATED,entity.id,null,MasterDataAudit.state(entity));
        return Site.of(entity);
    }

    @Transactional(readOnly = true)
    Items<Site> sites(AuthenticatedUserPrincipal user, UUID tenant) {
        return sites(user,tenant,null);
    }
    @Transactional(readOnly = true)
    Items<Site> sites(AuthenticatedUserPrincipal user, UUID tenant, UUID customer) {
        var scope = authorization.resolveCustomerScope(user, tenant, SITE_VIEW);
        if(customer!=null) {
            authorization.requireCustomerCapability(user,tenant,customer,SITE_VIEW);
            repository.requireActiveTenant(tenant);
            scope=new CustomerAccessScope(false,Set.of(customer));
        }
        return new Items<>(repository.sites(tenant, scope, null).stream().map(Site::of).toList());
    }

    @Transactional(readOnly = true)
    Site site(AuthenticatedUserPrincipal user, UUID tenant, UUID id) {
        var scope = authorization.resolveCustomerScope(user, tenant, SITE_VIEW);
        return Site.of(visibleSite(tenant, id, scope));
    }

    private PhysicalSiteEntity visibleSite(UUID tenant, UUID id, CustomerAccessScope scope) {
        return repository.sites(tenant, scope, id).stream().findFirst().orElseThrow(MasterDataFailure::missing);
    }

    Site updateSite(AuthenticatedUserPrincipal user, UUID tenant, UUID id, Map<String, Object> fields) {
        authorization.requireTenantCapability(user, tenant, SITE_EDIT);
        long expected = patch(fields, Set.of("expectedVersion", "name", "address", "timezone", "latitude",
                "longitude", "geofenceRadiusM", "active"));
        var entity = repository.resource(PhysicalSiteEntity.class, tenant, id);
        entity.requireVersion(expected);
        var before = MasterDataAudit.state(entity);
        if (fields.containsKey("name")) { entity.name = text(fields.get("name"), "name", false); }
        if (fields.containsKey("address")) { address(entity, fields.get("address")); }
        if (fields.containsKey("timezone")) { entity.timezone = timezone(fields.get("timezone")); }
        if (fields.containsKey("latitude")) { entity.latitude = coordinate(fields.get("latitude"), "latitude", 90); }
        if (fields.containsKey("longitude")) { entity.longitude = coordinate(fields.get("longitude"), "longitude", 180); }
        if (fields.containsKey("geofenceRadiusM")) { entity.geofenceRadiusM = radius(fields.get("geofenceRadiusM")); }
        if (fields.containsKey("active")) { entity.active = bool(fields.get("active")); }
        repository.flush();
        audit.human(tenant,user.userId(),SITE_UPDATED,entity.id,before,MasterDataAudit.state(entity));
        return Site.of(entity);
    }

    CustomerSite associate(AuthenticatedUserPrincipal user, UUID tenant, UUID customer, AssociateSite request) {
        authorization.requireTenantCapability(user, tenant, CUSTOMER_EDIT);
        if (request.siteId() == null) { throw MasterDataFailure.invalid("siteId"); }
        repository.resource(CustomerEntity.class, tenant, customer);
        repository.resource(PhysicalSiteEntity.class, tenant, request.siteId());
        var entity = new CustomerSiteEntity();
        entity.id = UUID.randomUUID(); entity.tenantId = tenant; entity.customerId = customer;
        entity.siteId = request.siteId(); entity.createdAt = Instant.now();
        repository.insert(entity); repository.flush();
        audit.human(tenant,user.userId(),CUSTOMER_SITE_ASSOCIATED,customer,snapshot("siteId",entity.siteId,"associationId",null,"associated",false),snapshot("siteId",entity.siteId,"associationId",entity.id,"associated",true));
        return CustomerSite.of(entity);
    }

    void disassociate(AuthenticatedUserPrincipal user, UUID tenant, UUID customer, UUID site) {
        authorization.requireTenantCapability(user, tenant, CUSTOMER_EDIT);
        var association = repository.lockAssociation(tenant, customer, site, true);
        if (repository.hasActiveLocations(tenant, customer, site)
                || siteUsageGuards.stream().anyMatch(guard -> guard.hasActiveUse(tenant, customer, site))) {
            throw MasterDataFailure.conflict();
        }
        repository.deleteAssociation(tenant, association);
        audit.human(tenant,user.userId(),CUSTOMER_SITE_DISASSOCIATED,customer,snapshot("siteId",site,"associationId",association,"associated",true),snapshot("siteId",site,"associationId",association,"associated",false));
    }

    Location createLocation(AuthenticatedUserPrincipal user, UUID tenant, UUID site, CreateLocation request) {
        authorization.requireTenantCapability(user, tenant, SITE_CREATE);
        repository.resource(PhysicalSiteEntity.class, tenant, site);
        var entity = new OperationalLocationEntity();
        entity.initialize(tenant, text(request.name(), "name", false));
        entity.siteId = site; entity.customerId = request.customerId();
        entity.type = choice(request.type(), "type", LOCATION_TYPES);
        entity.code = text(request.code(), "code", true);
        requireAssociation(entity);
        repository.insert(entity); repository.flush();
        audit.human(tenant,user.userId(),LOCATION_CREATED,entity.id,null,MasterDataAudit.state(entity));
        return Location.of(entity);
    }

    private void requireAssociation(OperationalLocationEntity entity) {
        if (entity.customerId != null) {
            repository.resource(CustomerEntity.class, entity.tenantId, entity.customerId);
            repository.lockAssociation(entity.tenantId, entity.customerId, entity.siteId, false);
        }
    }

    @Transactional(readOnly = true)
    Items<Location> locations(AuthenticatedUserPrincipal user, UUID tenant, UUID site) {
        return locations(user,tenant,site,null);
    }
    @Transactional(readOnly = true)
    Items<Location> locations(AuthenticatedUserPrincipal user, UUID tenant, UUID site, UUID customer) {
        var scope = authorization.resolveCustomerScope(user, tenant, SITE_VIEW);
        if(customer!=null) {
            authorization.requireCustomerCapability(user,tenant,customer,SITE_VIEW);
            repository.requireActiveTenant(tenant);
            scope=new CustomerAccessScope(false,Set.of(customer));
        }
        visibleSite(tenant, site, scope);
        return new Items<>(repository.locations(tenant, site, scope).stream().map(Location::of).toList());
    }

    Location updateLocation(AuthenticatedUserPrincipal user, UUID tenant, UUID id, Map<String, Object> fields) {
        authorization.requireTenantCapability(user, tenant, SITE_EDIT);
        long expected = patch(fields, Set.of("expectedVersion", "name", "type", "code", "customerId", "active"));
        var entity = repository.resource(OperationalLocationEntity.class, tenant, id);
        entity.requireVersion(expected);
        var before = MasterDataAudit.state(entity);
        var oldCustomer = entity.customerId;
        if (fields.containsKey("name")) { entity.name = text(fields.get("name"), "name", false); }
        if (fields.containsKey("type")) { entity.type = choice(fields.get("type"), "type", LOCATION_TYPES); }
        if (fields.containsKey("code")) { entity.code = text(fields.get("code"), "code", true); }
        if (fields.containsKey("customerId")) { entity.customerId = customerId(fields.get("customerId")); }
        if (fields.containsKey("active")) { entity.active = bool(fields.get("active")); }
        // Inactive historical locations may retain an association removed since deactivation.
        if (entity.active || !Objects.equals(oldCustomer, entity.customerId)) { requireAssociation(entity); }
        repository.flush();
        audit.human(tenant,user.userId(),LOCATION_UPDATED,entity.id,before,MasterDataAudit.state(entity));
        return Location.of(entity);
    }
}
