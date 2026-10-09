package com.trailersaas.orders.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class OrderContracts {
    private OrderContracts() {}
    record Create(UUID customerId, String orderNumber, String poNumber, String shipmentNumber, String bolReference,
            List<CargoInput> cargoLines, List<StopInput> requestedStops) {}
    record CargoInput(String description, BigDecimal quantity, Integer palletCount, BigDecimal weight, String weightUnit) {}
    record StopInput(int sequenceNumber, UUID siteId, UUID locationId, Map<String, Object> siteSnapshot,
            String stopType, Instant scheduledAt, String instructions) {}
    record Summary(UUID id, UUID customerId, String orderNumber, String poNumber, String shipmentNumber,
            String bolReference, OrderStatus status, UUID createdByUserId, long version, Instant createdAt,
            Instant updatedAt, Instant deliveredAt, Instant completedAt, Instant cancelledAt) {
        static Summary of(OrderEntity e) { return new Summary(e.id, e.customerId, e.orderNumber, e.poNumber,
                e.shipmentNumber, e.bolReference, e.status, e.createdByUserId, e.version, e.createdAt, e.updatedAt,
                e.deliveredAt, e.completedAt, e.cancelledAt); }
    }
    record Cargo(UUID id, String description, BigDecimal quantity, Integer palletCount, BigDecimal weight,
            String weightUnit, Instant createdAt) {
        static Cargo of(OrderCargoEntity e) { return new Cargo(e.id, e.description, e.quantity, e.palletCount,
                e.weight, e.weightUnit, e.createdAt); }
    }
    record Stop(UUID id, int sequenceNumber, UUID siteId, UUID locationId, Map<String, Object> siteSnapshot,
            String stopType, Instant scheduledAt, String instructions, long version, Instant createdAt, Instant updatedAt) {
        static Stop of(OrderStopEntity e) { return new Stop(e.id, e.sequenceNumber, e.siteId, e.locationId,
                e.siteSnapshot, e.stopType, e.scheduledAt, e.instructions, e.version, e.createdAt, e.updatedAt); }
    }
    record Detail(UUID id, UUID customerId, String orderNumber, String poNumber, String shipmentNumber,
            String bolReference, OrderStatus status, UUID createdByUserId, long version, Instant createdAt,
            Instant updatedAt, Instant deliveredAt, Instant completedAt, Instant cancelledAt,
            List<Cargo> cargoLines, List<Stop> requestedStops) {
        static Detail of(OrderEntity e, List<OrderCargoEntity> cargo, List<OrderStopEntity> stops) {
            return new Detail(e.id, e.customerId, e.orderNumber, e.poNumber, e.shipmentNumber, e.bolReference,
                    e.status, e.createdByUserId, e.version, e.createdAt, e.updatedAt, e.deliveredAt, e.completedAt,
                    e.cancelledAt, cargo.stream().map(Cargo::of).toList(), stops.stream().map(Stop::of).toList());
        }
    }
    record Page(List<Summary> items, String nextCursor) {}
}
