package com.trailersaas.orders.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.trailersaas.customers.OrderDestinationPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.orders.OrderLifecycleEvent;
import com.trailersaas.orders.OrderLifecycleEvent.*;
import jakarta.persistence.OptimisticLockException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.orders.internal.OrderContracts.Detail;

@Service @Transactional
class OrderMutationService {
    private final TenantAuthorization authorization;
    private final TenantActorMembership attribution;
    private final OrderRepository repository;
    private final ManifestService manifests;
    private final OrderDestinationPolicy destinations;
    private final ApplicationEventPublisher events;
    private final List<com.trailersaas.orders.OrderPreExecutionPlanningRelease> planningReleases;
    private final CustomerActionAccess actions;
    private final OrderRequestEditor requestEditor;
    OrderMutationService(TenantAuthorization authorization, TenantActorMembership attribution, OrderRepository repository,
            ManifestService manifests, OrderDestinationPolicy destinations, ApplicationEventPublisher events, List<com.trailersaas.orders.OrderPreExecutionPlanningRelease> planningReleases, CustomerActionAccess actions, OrderRequestEditor requestEditor) {
        this.authorization = authorization; this.attribution = attribution; this.repository = repository;
        this.manifests = manifests; this.destinations = destinations; this.events = events; this.planningReleases = List.copyOf(planningReleases);
        this.actions=actions;
        this.requestEditor=requestEditor;
    }
    Detail patch(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Map<String, Object> input) {
        long expected = OrderMutationValidation.version(input, Set.of("expectedVersion", "poNumber", "shipmentNumber", "bolReference", "cargoLines", "requestedStops"));
        for(var field:List.of("cargoLines","requestedStops")) if(input.containsKey(field) && !(input.get(field) instanceof List<?>)) throw OrderFailure.invalid(field);
        for (var field : List.of("poNumber", "shipmentNumber", "bolReference")) {
            if (input.containsKey(field)) { OrderValidation.text(input.get(field), field, true); }
        }
        var order = authorized(actor, tenant, id, Capability.ORDER_EDIT, expected);
        OrderStateMachine.next(order.status, Action.ORDER_EDITED);
        boolean content=input.containsKey("cargoLines")||input.containsKey("requestedStops");
        if(content) requestEditor.replace(order,input,actor.userId());
        var po = input.containsKey("poNumber") ? (String) input.get("poNumber") : order.poNumber;
        var shipment = input.containsKey("shipmentNumber") ? (String) input.get("shipmentNumber") : order.shipmentNumber;
        var bol = input.containsKey("bolReference") ? (String) input.get("bolReference") : order.bolReference;
        if (content || !Objects.equals(po, order.poNumber) || !Objects.equals(shipment, order.shipmentNumber) || !Objects.equals(bol, order.bolReference)) {
            var previous = order.status; order.poNumber = po; order.shipmentNumber = shipment; order.bolReference = bol;
            touch(order); flush(); publish(order, previous, Action.ORDER_EDITED, human(actor, tenant), null, null);
        }
        return detail(order);
    }
    Detail cancel(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Map<String, Object> input) {
        long expected = OrderMutationValidation.version(input, Set.of("expectedVersion", "reason", "resolution"));
        var reason = OrderValidation.text(input.get("reason"), "reason", false);
        var resolution = OrderMutationValidation.resolution(input.get("resolution"));
        var order = authorized(actor, tenant, id, Capability.ORDER_CANCEL, expected);
        var action = order.status == OrderStatus.IN_PROGRESS || order.status == OrderStatus.PARTIALLY_DELIVERED
                ? Action.ORDER_CANCELLATION_REQUESTED : Action.ORDER_CANCELLED;
        OrderStateMachine.next(order.status, action);
        if (action == Action.ORDER_CANCELLATION_REQUESTED && (resolution == null || resolution.instructions() == null)) {
            throw OrderFailure.invalid("resolution.instructions");
        }
        if (resolution != null && resolution.destinationSiteId() != null
                && !destinations.compatible(tenant, order.customerId, resolution.destinationSiteId(), null)) {
            throw OrderFailure.conflict();
        }
        if (action == Action.ORDER_CANCELLED) {
            manifests.releasePreExecutionAllocations(order);
            for (var release : planningReleases) { release.release(tenant, order.id); }
        }
        transition(order, action, human(actor, tenant), reason, resolution);
        return detail(order);
    }
    Detail complete(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Map<String, Object> input) {
        long expected = OrderMutationValidation.version(input, Set.of("expectedVersion"));
        var order = authorized(actor, tenant, id, Capability.ORDER_COMPLETE, expected);
        transition(order, Action.ORDER_COMPLETED, human(actor, tenant), null, null); return detail(order);
    }
    private OrderEntity authorized(AuthenticatedUserPrincipal actor, UUID tenant, UUID id, Capability capability, long expected) {
        var scope = authorization.resolveCustomerScope(actor, tenant, capability);
        var order = repository.lockForMutation(tenant, id, scope);
        if(!scope.tenantWide()) actions.capabilities(actor,tenant,order.customerId);
        expected(order, expected); return order;
    }
    static void expected(OrderEntity order, long expected) {
        if (expected < 0) { throw OrderFailure.invalid("expectedVersion"); }
        if (order.version != expected) { throw OrderFailure.stale(); }
    }
    void transition(OrderEntity order, Action action, Actor actor, String reason, Resolution resolution) {
        var previous = order.status;
        order.status = OrderStateMachine.next(previous, action); touch(order);
        switch (order.status) {
            case DELIVERED -> order.deliveredAt = order.updatedAt;
            case COMPLETED -> order.completedAt = order.updatedAt;
            case CANCELLED -> order.cancelledAt = order.updatedAt;
            default -> { }
        }
        flush(); publish(order, previous, action, actor, reason, resolution);
    }
    private Actor human(AuthenticatedUserPrincipal actor, UUID tenant) {
        return new Actor("HUMAN_USER", "USER_ACTION", actor.userId(), attribution.forActor(actor, tenant));
    }
    private void publish(OrderEntity order, OrderStatus previous, Action action, Actor actor, String reason, Resolution resolution) {
        events.publishEvent(new OrderLifecycleEvent(order.tenantId, order.id, order.customerId, action,
                previous.name(), order.status.name(), order.version, actor, reason, resolution, order.updatedAt));
    }
    private static void touch(OrderEntity order) {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        order.updatedAt = now.isAfter(order.updatedAt) ? now : order.updatedAt.plus(1, ChronoUnit.MICROS);
    }
    private void flush() {
        try { repository.flush(); }
        catch (OptimisticLockException | OptimisticLockingFailureException failure) { throw OrderFailure.stale(); }
    }
    private Detail detail(OrderEntity order) {
        return Detail.of(order, repository.cargo(order.tenantId, order.id), repository.stops(order.tenantId, order.id));
    }
}
