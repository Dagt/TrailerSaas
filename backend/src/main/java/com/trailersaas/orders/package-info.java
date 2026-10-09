/**
 * Customer Orders, cargo and requested destinations, independent of execution/planning.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"identity", "customers", "trailers"})
package com.trailersaas.orders;
