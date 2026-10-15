package com.trailersaas.orders.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import com.trailersaas.customers.OrderDestinationPolicy;
import com.trailersaas.identity.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.trailersaas.orders.internal.OrderContracts.*;

@Service
@Transactional
class OrderService {
    private final TenantAuthorization authorization;
    private final OrderAccessPolicy access;
    private final OrderDestinationPolicy destinations;
    private final OrderRepository repository;
    private final ManifestService manifests;
    private final com.trailersaas.orders.OrderPlanningDirectory planningDirectory;
    private final CustomerActionAccess actions;
    private final com.trailersaas.orders.CustomerOrderProgress progress;
    private final OrderRequestEditor requestEditor;
    OrderService(TenantAuthorization authorization, OrderAccessPolicy access, OrderDestinationPolicy destinations, OrderRepository repository, ManifestService manifests, com.trailersaas.orders.OrderPlanningDirectory planningDirectory, CustomerActionAccess actions, com.trailersaas.orders.CustomerOrderProgress progress, OrderRequestEditor requestEditor) {
        this.authorization = authorization; this.access = access; this.destinations = destinations; this.repository = repository;
        this.manifests = manifests; this.planningDirectory = planningDirectory;
        this.actions=actions; this.progress=progress;
        this.requestEditor=requestEditor;
    }

    Detail create(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, Object> input) {
        var request = OrderValidation.create(input);
        authorization.requireCustomerCapability(actor, tenant, request.customerId(), Capability.ORDER_CREATE);
        if(!authorization.resolveCustomerScope(actor,tenant,Capability.ORDER_CREATE).tenantWide()) actions.capabilities(actor,tenant,request.customerId());
        if (request.plannedTrailerId() != null) {
            authorization.requireCustomerCapability(actor, tenant, request.customerId(), Capability.TRIP_ASSIGN_TRAILER);
            manifests.validateTrailer(actor, tenant, request.plannedTrailerId());
        }
        if (!destinations.customerExists(tenant, request.customerId())) { throw OrderFailure.conflict(); }
        // Validate normalized resources against the Order's already-authorized Customer, not snapshot text/claims.
        for (var stop : request.requestedStops()) {
            if (stop.siteId() != null && !destinations.compatible(tenant, request.customerId(), stop.siteId(), stop.locationId())) {
                throw OrderFailure.conflict();
            }
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var order = new OrderEntity(); order.id = UUID.randomUUID(); order.tenantId = tenant;
        order.customerId = request.customerId(); order.orderNumber = request.orderNumber(); order.poNumber = request.poNumber();
        order.shipmentNumber = request.shipmentNumber(); order.bolReference = request.bolReference(); order.status = OrderStatus.OPEN;
        order.createdByUserId = actor.userId(); order.createdAt = order.updatedAt = now;
        repository.insert(order);
        for (var item : request.cargoLines()) {
            var cargo = new OrderCargoEntity(); cargo.id = UUID.randomUUID(); cargo.tenantId = tenant; cargo.orderId = order.id;
            cargo.description = item.description(); cargo.quantity = item.quantity(); cargo.palletCount = item.palletCount();
            cargo.weight = item.weight(); cargo.weightUnit = item.weightUnit(); cargo.createdAt = now;
            repository.insert(cargo);
        }
        for (var item : request.requestedStops()) {
            var stop = new OrderStopEntity(); stop.id = UUID.randomUUID(); stop.tenantId = tenant; stop.orderId = order.id;
            stop.sequenceNumber = item.sequenceNumber(); stop.siteId = item.siteId(); stop.locationId = item.locationId();
            stop.siteSnapshot = item.siteSnapshot(); stop.stopType = item.stopType(); stop.scheduledAt = item.scheduledAt();
            stop.instructions = item.instructions(); stop.createdAt = stop.updatedAt = now;
            repository.insert(stop);
        }
        try { repository.flush(); }
        catch (DataIntegrityViolationException | ConstraintViolationException failure) {
            if (!OrderFailure.expectedIntegrity(failure)) { throw failure; }
            throw OrderFailure.conflict();
        }
        if (request.plannedTrailerId() != null) { manifests.planCreatedOrder(actor, order, request.plannedTrailerId()); }
        return detail(order);
    }

    @Transactional(readOnly = true)
    Page list(AuthenticatedUserPrincipal actor, UUID tenant, Map<String, String> parameters) {
        var scope = access.readScope(actor, tenant);
        if(!scope.tenantWide()) for(var customer:scope.customerIds()) actions.capabilities(actor,tenant,customer);
        var filters = OrderValidation.filters(parameters);
        if (filters.customerId() != null && !scope.tenantWide() && !scope.customerIds().contains(filters.customerId())) { throw OrderFailure.scope(); }
        var rows = filters.unplanned() == null ? repository.list(tenant, scope, filters) : repository.byPlanningIds(tenant, scope,
                planningDirectory.page(new com.trailersaas.orders.OrderPlanningDirectory.Query(tenant, scope, filters.customerId(),
                    filters.status() == null ? null : filters.status().name(), filters.cursor() == null ? null : filters.cursor().createdAt(),
                    filters.cursor() == null ? null : filters.cursor().id(), filters.limit() + 1, filters.unplanned())));
        boolean more = rows.size() > filters.limit();
        var page = more ? rows.subList(0, filters.limit()) : rows;
        var last = page.isEmpty() ? null : page.getLast();
        return new Page(page.stream().map(Summary::of).toList(), more ? new OrderValidation.Position(last.createdAt, last.id).encode() : null);
    }
    @Transactional(readOnly = true)
    Detail get(AuthenticatedUserPrincipal actor, UUID tenant, UUID id) {
        var scope = access.readScope(actor, tenant);
        if(!scope.tenantWide()) for(var customer:scope.customerIds()) actions.capabilities(actor,tenant,customer);
        return detail(repository.resource(tenant, id, scope));
    }
    @Transactional(readOnly=true)
    java.util.Set<Capability> customerActions(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer) {
        authorization.requireCustomerCapability(actor,tenant,customer,Capability.ORDER_CREATE);
        return actions.capabilities(actor,tenant,customer);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Progress progress(AuthenticatedUserPrincipal actor,UUID tenant,UUID id,Map<String,String> parameters) {
        OrderValidation.fields(parameters,java.util.Set.of("cursor","limit"),"query");
        var scope=access.readScope(actor,tenant);
        var order=repository.resource(tenant,id,scope);
        UUID after=parameters.containsKey("cursor")?OrderValidation.uuid(parameters.get("cursor"),"cursor",false):null;
        int limit=20;
        try { if(parameters.containsKey("limit")) limit=Integer.parseInt(parameters.get("limit")); } catch(NumberFormatException e) { throw OrderFailure.invalid("limit"); }
        if(limit<1||limit>50) throw OrderFailure.invalid("limit");
        var page=progress.read(actor,tenant,id,order.customerId,after,limit);
        return new Progress(order.id,order.customerId,order.version,requestEditor.editable(order),page.items(),page.nextCursor(),requestEditor.history(order));
    }
    private Detail detail(OrderEntity order) {
        return Detail.of(order, repository.cargo(order.tenantId, order.id), repository.stops(order.tenantId, order.id));
    }
}
