package com.trailersaas.jobs.internal;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trailersaas.jobs")
record JobsProperties(@DefaultValue("false") boolean enabled, List<UUID> tenants,
        @DefaultValue("5s") Duration pollInterval, @DefaultValue("4") int batchSize,
        @DefaultValue("4") int concurrency, @DefaultValue("30s") Duration handlerTimeout,
        @DefaultValue("2m") Duration lease, @DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration initialDelay, @DefaultValue("30m") Duration maxBackoff) {
    JobsProperties {
        tenants=tenants==null ? List.of() : List.copyOf(tenants);
        if (tenants.size()>1000 || new HashSet<>(tenants).size()!=tenants.size() ||
                pollInterval.compareTo(Duration.ofSeconds(1))<0 || pollInterval.compareTo(Duration.ofHours(1))>0 ||
                batchSize<1 || batchSize>100 || concurrency<1 || concurrency>32 ||
                handlerTimeout.isNegative() || handlerTimeout.isZero() || handlerTimeout.compareTo(Duration.ofMinutes(5))>0 ||
                lease.compareTo(handlerTimeout.multipliedBy(2))<0 || lease.compareTo(Duration.ofMinutes(30))>0 ||
                maxAttempts<1 || maxAttempts>20 || initialDelay.compareTo(Duration.ofSeconds(1))<0 ||
                maxBackoff.compareTo(initialDelay)<0 || maxBackoff.compareTo(Duration.ofDays(1))>0 ||
                (enabled && tenants.isEmpty())) {
            throw new IllegalArgumentException("Invalid bounded jobs configuration");
        }
    }
}
