package com.trailersaas.notifications.internal;

import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Test-only access to package-local scheduling; production exposes no manual scan HTTP. */
@TestConfiguration(proxyBeanMethods=false)
public class StaleDetectorTestAccess {
 @Bean public Harness staleHarness(StaleScanScheduler scheduler){return new Harness(scheduler);}
 public static class Harness {
  private final StaleScanScheduler scheduler;
  Harness(StaleScanScheduler scheduler){this.scheduler=scheduler;}
  public boolean schedule(UUID tenant){return scheduler.schedule(tenant);}
 }
}
