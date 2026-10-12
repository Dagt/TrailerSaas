package com.trailersaas.inspections.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import com.trailersaas.trips.DriverDeliveryExecution;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}")
class RoadOperationsController {
  private final RoadOperationsService service;
  RoadOperationsController(RoadOperationsService service){this.service=service;}
  @PostMapping(value="/stops/{stopId}/delivery-completions",consumes="application/json")
  DriverDeliveryExecution.Result delivery(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID stopId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query){return service.delivery(actor,tenantId,stopId,key,ShiftController.parse(body,query,RoadContracts.Delivery.class));}
  @PostMapping(value="/driver/me/fuel-events",consumes="application/json")
  RoadContracts.FuelResult fuel(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query){return service.fuel(actor,tenantId,key,ShiftController.parse(body,query,RoadContracts.Fuel.class));}
  @PostMapping(value="/trips/{tripId}/exceptions",consumes="application/json")
  RoadContracts.ProblemResult problem(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID tripId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query){return service.problem(actor,tenantId,tripId,key,ShiftController.parse(body,query,RoadContracts.Problem.class));}
}
