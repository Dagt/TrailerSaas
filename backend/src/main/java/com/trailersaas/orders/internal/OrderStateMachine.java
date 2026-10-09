package com.trailersaas.orders.internal;

import com.trailersaas.orders.OrderLifecycleEvent.Action;
import static com.trailersaas.orders.internal.OrderStatus.*;

final class OrderStateMachine {
    private OrderStateMachine() {}
    static OrderStatus next(OrderStatus state, Action action) {
        var next = switch (action) {
            case ORDER_EDITED -> state == OPEN || state == PLANNED ? state : null;
            case ORDER_PLANNED -> state == OPEN ? PLANNED : null;
            case ORDER_FULFILLMENT_STARTED -> state == PLANNED ? IN_PROGRESS : null;
            case ORDER_PARTIALLY_DELIVERED -> state == IN_PROGRESS ? PARTIALLY_DELIVERED : null;
            case ORDER_FULFILLMENT_RESUMED -> state == PARTIALLY_DELIVERED ? IN_PROGRESS : null;
            case ORDER_DELIVERED -> state == IN_PROGRESS || state == PARTIALLY_DELIVERED ? DELIVERED : null;
            case ORDER_CANCELLED -> state == OPEN || state == PLANNED ? CANCELLED : null;
            case ORDER_CANCELLATION_REQUESTED -> state == IN_PROGRESS || state == PARTIALLY_DELIVERED ? CANCELLING : null;
            case ORDER_CANCELLATION_FINISHED -> state == CANCELLING ? CANCELLED : null;
            case ORDER_COMPLETED -> state == DELIVERED ? COMPLETED : null;
        };
        if (next == null) { throw OrderFailure.transition(); } return next;
    }
}
