/**
 * Shift/inspection orchestration, consuming public domain ports without reverse dependencies.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies={"identity","drivers","tractors","trips","documents"})
package com.trailersaas.inspections;
