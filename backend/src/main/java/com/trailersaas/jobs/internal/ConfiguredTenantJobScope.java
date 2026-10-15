package com.trailersaas.jobs.internal;

import com.trailersaas.jobs.TenantJobScope;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ConfiguredTenantJobScope implements TenantJobScope {
 private final JobsProperties settings;
 ConfiguredTenantJobScope(JobsProperties settings){this.settings=settings;}
 public boolean permits(UUID tenant){return settings.enabled()&&settings.tenants().contains(tenant);}
}
