package com.trailersaas.trips.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class DriverTripExecutionController {
  private final DriverTripExecutionService service;
  DriverTripExecutionController(DriverTripExecutionService service) { this.service=service; }
  @GetMapping("/driver/me/trips/{tripId}")
  DriverTripExecutionService.Detail detail(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID tripId,@RequestParam Map<String,String> query) {
    TripValidation.fields(query,Set.of()); return service.get(actor,tenantId,tripId);
  }
  @PostMapping("/trips/{tripId}/departures")
  DriverTripExecutionService.Detail first(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID tripId,@RequestHeader(name="Idempotency-Key",required=false) String key,
      @RequestBody Map<String,Object> body,@RequestParam Map<String,String> query) {
    TripValidation.fields(query,Set.of()); return service.command(actor,tenantId,tripId,TripExecutionRequests.Action.FIRST_DEPARTURE,key,body);
  }
  @PostMapping("/stops/{stopId}/{command:arrivals|service-starts|completions|departures}")
  DriverTripExecutionService.Detail stop(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
      @PathVariable UUID tenantId,@PathVariable UUID stopId,@PathVariable String command,
      @RequestHeader(name="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body,
      @RequestParam Map<String,String> query) {
    TripValidation.fields(query,Set.of());
    var action=switch(command) {
      case "arrivals" -> TripExecutionRequests.Action.ARRIVAL;
      case "service-starts" -> TripExecutionRequests.Action.SERVICE;
      case "completions" -> TripExecutionRequests.Action.COMPLETE;
      case "departures" -> TripExecutionRequests.Action.DEPARTURE;
      default -> throw TripFailure.invalid("command");
    };
    return service.command(actor,tenantId,stopId,action,key,body);
  }
}
