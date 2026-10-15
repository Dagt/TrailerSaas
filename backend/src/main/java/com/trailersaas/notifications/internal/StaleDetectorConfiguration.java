package com.trailersaas.notifications.internal;

import com.trailersaas.jobs.TenantJobScope;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(StaleDetectorProperties.class)
class StaleDetectorConfiguration {
 @Bean StaleDeploymentAuthorization staleDeploymentAuthorization(StaleDetectorProperties settings,TenantJobScope workers){
  if(settings.enabled()&&settings.tenants().stream().anyMatch(t->!workers.permits(t)))
   throw new IllegalArgumentException("Enabled detector Tenants must belong to the enabled worker allowlist");
  return new StaleDeploymentAuthorization(settings,workers);
 }
 record StaleDeploymentAuthorization(StaleDetectorProperties settings,TenantJobScope workers){
  boolean permits(java.util.UUID tenant){return settings.enabled()&&settings.tenants().contains(tenant)&&workers.permits(tenant);}
 }
}
