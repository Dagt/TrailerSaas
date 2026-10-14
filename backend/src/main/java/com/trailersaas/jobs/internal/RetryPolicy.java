package com.trailersaas.jobs.internal;

import java.time.*;
import java.util.function.DoubleSupplier;

final class RetryPolicy {
    private final Duration initial, cap;
    private final DoubleSupplier jitter;
    RetryPolicy(Duration initial, Duration cap, DoubleSupplier jitter) {
        this.initial=initial; this.cap=cap; this.jitter=jitter;
    }
    Duration delay(int attempt) {
        if (attempt<1 || attempt>20) throw new IllegalArgumentException("Invalid attempt");
        double random=jitter.getAsDouble();
        if (!Double.isFinite(random) || random<0 || random>1) throw new IllegalArgumentException("Invalid jitter");
        long exponential=Math.min(cap.toMillis(),Math.multiplyExact(initial.toMillis(),1L << (attempt-1)));
        return Duration.ofMillis(Math.max(1,Math.min(cap.toMillis(),(long)(exponential*(0.8+random*0.4)))));
    }
    Instant nextAt(Instant databaseNow, int attempt) { return databaseNow.plus(delay(attempt)); }
}
