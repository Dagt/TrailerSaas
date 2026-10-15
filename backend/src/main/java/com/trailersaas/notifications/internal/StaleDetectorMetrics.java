package com.trailersaas.notifications.internal;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class StaleDetectorMetrics {
 private final MeterRegistry registry;
 private final Map<UUID,Long> dead=new ConcurrentHashMap<>();
 StaleDetectorMetrics(MeterRegistry registry){this.registry=registry;registry.gauge("stale.detector.jobs.dead",dead,m->m.values().stream().mapToLong(Long::longValue).sum());}
 void count(String name){add(name,1);}
 void add(String name,long n){if(n>0)registry.counter("stale.detector."+name).increment(n);}
 void duration(Duration elapsed){registry.timer("stale.detector.scan.duration").record(elapsed.isNegative()?Duration.ZERO:elapsed);}
 void dead(UUID tenant,long count){dead.put(tenant,count);}
}
