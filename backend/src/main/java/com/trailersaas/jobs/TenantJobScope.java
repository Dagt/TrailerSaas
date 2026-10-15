package com.trailersaas.jobs;

import java.util.UUID;

/** Deployment authorization for domain schedulers; not an HTTP/User grant. */
public interface TenantJobScope { boolean permits(UUID tenantId); }
