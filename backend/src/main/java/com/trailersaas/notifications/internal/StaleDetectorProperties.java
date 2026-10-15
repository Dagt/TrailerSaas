package com.trailersaas.notifications.internal;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trailersaas.stale-detector")
record StaleDetectorProperties(@DefaultValue("false") boolean enabled,List<UUID> tenants,
 @DefaultValue("30s") Duration pollInterval,@DefaultValue("1h") Duration scanInterval,
 @DefaultValue("100") int batchSize){
 StaleDetectorProperties{
  tenants=tenants==null?List.of():List.copyOf(tenants);
  if(tenants.size()>1000||new HashSet<>(tenants).size()!=tenants.size()
   ||enabled&&tenants.isEmpty()||batchSize<1||batchSize>500
   ||pollInterval.compareTo(Duration.ofSeconds(5))<0||pollInterval.compareTo(Duration.ofHours(1))>0
   ||scanInterval.compareTo(Duration.ofMinutes(1))<0||scanInterval.compareTo(Duration.ofDays(1))>0)
   throw new IllegalArgumentException("Invalid bounded stale detector configuration");
 }
}
