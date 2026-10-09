package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import com.trailersaas.identity.*;
import com.trailersaas.trailers.TrailerPlanningPolicy;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal planning commands. No public Manifest CRUD/read surface or physical Trailer writes. */
@Service @Transactional
class ManifestService {
    /** A scoped allocation command reveals no other allocations or Trailer/customer metadata. */
    record Revision(UUID id, long version, Instant updatedAt) {
        static Revision of(LoadManifestEntity m) { return new Revision(m.id, m.version, m.updatedAt); }
    }
    record Manifest(UUID id, UUID trailerId, String status, long version, Instant createdAt, Instant updatedAt, Instant closedAt) {
        static Manifest of(LoadManifestEntity m) { return new Manifest(m.id, m.trailerId, m.status, m.version, m.createdAt, m.updatedAt, m.closedAt); }
    }
    private final TenantAuthorization authorization;
    private final TrailerPlanningPolicy trailers;
    private final ManifestRepository repository;
    private final OrderRepository orders;
    ManifestService(TenantAuthorization authorization, TrailerPlanningPolicy trailers, ManifestRepository repository, OrderRepository orders) {
        this.authorization = authorization; this.trailers = trailers; this.repository = repository; this.orders = orders;
    }
    Manifest create(AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer) {
        authorization.resolveCustomerScope(actor, tenant, Capability.TRIP_ASSIGN_TRAILER);
        validateTrailer(actor, tenant, trailer);
        var manifest = open(tenant, trailer); flush(); return Manifest.of(manifest);
    }
    /** Included in Order creation, not an existing-Manifest mutation: initial version stays zero. */
    void planCreatedOrder(AuthenticatedUserPrincipal actor, OrderEntity order, UUID trailer) {
        authorization.requireCustomerCapability(actor, order.tenantId, order.customerId, Capability.TRIP_ASSIGN_TRAILER);
        validateTrailer(actor, order.tenantId, trailer);
        var manifest = open(order.tenantId, trailer);
        insertAllocation(manifest, order, null, null, null); flush();
    }
    void validateTrailer(AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer) {
        if (trailer == null) { return; }
        switch (trailers.check(actor, tenant, trailer)) {
            case ELIGIBLE -> { }
            case NOT_FOUND -> throw OrderFailure.missing();
            case CUSTOMER_SCOPE_DENIED -> throw OrderFailure.scope();
            case UNAVAILABLE -> throw OrderFailure.conflict();
        }
    }
    Revision allocate(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, long expectedVersion, UUID orderId,
            BigDecimal quantity, Integer pallets, BigDecimal weight) {
        amounts(quantity, pallets, weight);
        var scope = authorization.resolveCustomerScope(actor, tenant, Capability.TRIP_ASSIGN_TRAILER);
        var manifest = mutable(tenant, id, expectedVersion);
        var order = orders.resource(tenant, orderId, scope);
        requireOpen(order);
        if (repository.allocation(tenant, id, orderId) != null) { throw OrderFailure.conflict(); }
        insertAllocation(manifest, order, quantity, pallets, weight); touch(manifest); flush(); return Revision.of(manifest);
    }
    Revision remove(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, long expectedVersion, UUID orderId) {
        var scope = authorization.resolveCustomerScope(actor, tenant, Capability.TRIP_ASSIGN_TRAILER);
        var manifest = mutable(tenant, id, expectedVersion);
        var order = orders.resource(tenant, orderId, scope); requireOpen(order);
        var allocation = repository.allocation(tenant, id, orderId);
        if (allocation == null) { throw OrderFailure.missing(); }
        repository.remove(allocation); touch(manifest); flush(); return Revision.of(manifest);
    }
    Manifest close(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, long expectedVersion) {
        authorization.requireTenantCapability(actor, tenant, Capability.TRIP_ASSIGN_TRAILER);
        var manifest = mutable(tenant, id, expectedVersion);
        manifest.status = "CLOSED"; touch(manifest); manifest.closedAt = manifest.updatedAt;
        flush(); return Manifest.of(manifest);
    }
    private LoadManifestEntity open(UUID tenant, UUID trailer) {
        var m = new LoadManifestEntity(); m.id = UUID.randomUUID(); m.tenantId = tenant; m.trailerId = trailer;
        m.status = "OPEN"; m.createdAt = m.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        repository.insert(m); return m;
    }
    private LoadManifestEntity mutable(UUID tenant, UUID id, long expectedVersion) {
        if (expectedVersion < 0) { throw OrderFailure.invalid("expectedVersion"); }
        var m = repository.lock(tenant, id);
        if (m.version != expectedVersion) { throw OrderFailure.stale(); }
        if (!"OPEN".equals(m.status)) { throw OrderFailure.conflict(); }
        return m;
    }
    private void insertAllocation(LoadManifestEntity m, OrderEntity order, BigDecimal quantity, Integer pallets, BigDecimal weight) {
        var a = new ManifestOrderEntity(); a.id = UUID.randomUUID(); a.tenantId = m.tenantId; a.manifestId = m.id; a.orderId = order.id;
        a.allocatedQuantity = quantity; a.allocatedPalletCount = pallets; a.allocatedWeight = weight;
        a.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); repository.insert(a);
    }
    static void amounts(BigDecimal quantity, Integer pallets, BigDecimal weight) {
        if (quantity != null && quantity.signum() < 0) { throw OrderFailure.invalid("allocatedQuantity"); }
        if (pallets != null && pallets < 0) { throw OrderFailure.invalid("allocatedPalletCount"); }
        if (weight != null && weight.signum() < 0) { throw OrderFailure.invalid("allocatedWeight"); }
    }
    private void requireOpen(OrderEntity order) { if (order.status != OrderStatus.OPEN) { throw OrderFailure.conflict(); } }
    private void touch(LoadManifestEntity m) {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        m.updatedAt = now.isAfter(m.updatedAt) ? now : m.updatedAt.plus(1, ChronoUnit.MICROS);
    }
    private void flush() {
        try { repository.flush(); }
        catch (OptimisticLockException | OptimisticLockingFailureException failure) { throw OrderFailure.stale(); }
        catch (DataIntegrityViolationException | ConstraintViolationException failure) {
            if (!OrderFailure.expectedIntegrity(failure)) { throw failure; } throw OrderFailure.conflict();
        }
    }
}
