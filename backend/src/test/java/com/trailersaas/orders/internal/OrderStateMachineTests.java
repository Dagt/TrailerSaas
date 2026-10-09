package com.trailersaas.orders.internal;

import java.util.*;
import java.util.stream.Stream;
import com.trailersaas.orders.OrderLifecycleEvent.Action;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class OrderStateMachineTests {
    static Stream<Arguments> pairs() {
        return Arrays.stream(OrderStatus.values()).flatMap(s -> Arrays.stream(Action.values()).map(a -> Arguments.of(s, a)));
    }
    @ParameterizedTest @MethodSource("pairs")
    void exhaustiveAcceptedActionStateMatrixRejectsEveryOtherPair(OrderStatus state, Action action) {
        var allowed = switch (action) {
            case ORDER_EDITED, ORDER_CANCELLED -> Set.of("OPEN", "PLANNED");
            case ORDER_PLANNED -> Set.of("OPEN");
            case ORDER_FULFILLMENT_STARTED -> Set.of("PLANNED");
            case ORDER_PARTIALLY_DELIVERED -> Set.of("IN_PROGRESS");
            case ORDER_FULFILLMENT_RESUMED -> Set.of("PARTIALLY_DELIVERED");
            case ORDER_DELIVERED, ORDER_CANCELLATION_REQUESTED -> Set.of("IN_PROGRESS", "PARTIALLY_DELIVERED");
            case ORDER_COMPLETED -> Set.of("DELIVERED");
            case ORDER_CANCELLATION_FINISHED -> Set.of("CANCELLING");
        };
        if (!allowed.contains(state.name())) {
            assertThatThrownBy(() -> OrderStateMachine.next(state, action)).isInstanceOf(OrderFailure.class)
                    .extracting(e -> ((OrderFailure)e).code).isEqualTo("INVALID_STATE_TRANSITION");
        } else {
            var target = switch (action) {
                case ORDER_EDITED -> state;
                case ORDER_PLANNED -> OrderStatus.PLANNED;
                case ORDER_FULFILLMENT_STARTED, ORDER_FULFILLMENT_RESUMED -> OrderStatus.IN_PROGRESS;
                case ORDER_PARTIALLY_DELIVERED -> OrderStatus.PARTIALLY_DELIVERED;
                case ORDER_DELIVERED -> OrderStatus.DELIVERED;
                case ORDER_CANCELLED, ORDER_CANCELLATION_FINISHED -> OrderStatus.CANCELLED;
                case ORDER_CANCELLATION_REQUESTED -> OrderStatus.CANCELLING;
                case ORDER_COMPLETED -> OrderStatus.COMPLETED;
            };
            assertThat(OrderStateMachine.next(state, action)).isEqualTo(target);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "null", "negative", "decimal", "string", "unknown"})
    void expectedVersionIsRequiredNonnegativeIntegerAndBodyIsStrict(String mode) {
        var input = new HashMap<String,Object>();
        switch(mode) {
            case "null" -> input.put("expectedVersion", null);
            case "negative" -> input.put("expectedVersion", -1);
            case "decimal" -> input.put("expectedVersion", 1.5);
            case "string" -> input.put("expectedVersion", "0");
            case "unknown" -> { input.put("expectedVersion", 0); input.put("status", "CANCELLED"); }
            default -> { }
        }
        assertThatThrownBy(() -> OrderMutationValidation.version(input, Set.of("expectedVersion"))).isInstanceOf(OrderFailure.class);
    }
}
