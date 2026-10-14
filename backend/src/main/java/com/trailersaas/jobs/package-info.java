/** ADR-022 transactional durable work. Domain producers/handlers use this public port only. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "identity")
package com.trailersaas.jobs;
