package com.trailersaas.inspections.internal;

import com.trailersaas.drivers.DriverShiftContext;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.cfg.EnumFeature;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/driver/me")
class ShiftController {
  private final ShiftService shifts;
  private final InspectionService inspections;
  private static final JsonMapper STRICT=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
  ShiftController(ShiftService shifts,InspectionService inspections) {this.shifts=shifts;this.inspections=inspections;}
  static <T> T parse(String text,Map<String,String> query,Class<T> type) {
    if(text==null || text.length()>16384 || !query.isEmpty())throw ShiftFailure.invalid();
    try {return STRICT.readValue(text,type);}catch(RuntimeException e){throw ShiftFailure.invalid();}
  }
  static boolean contains(String text,String field){return STRICT.readTree(text).has(field);}
  @PostMapping(value="/shifts/on-duty",consumes="application/json")
  DriverShiftContext.Shift start(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query) {
    return shifts.start(actor,tenantId,key,parse(body,query,ShiftContracts.OnDuty.class));
  }
  @PostMapping(value="/shifts/{shiftId}/off-duty",consumes="application/json")
  DriverShiftContext.Shift end(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,@PathVariable UUID shiftId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query) {
    return shifts.end(actor,tenantId,shiftId,key,parse(body,query,ShiftContracts.OffDuty.class));
  }
  @PostMapping(value="/inspections/pre-trip",consumes="application/json")
  DriverShiftContext.Inspection pre(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query) {
    return inspections.pre(actor,tenantId,key,parse(body,query,ShiftContracts.PreTrip.class));
  }
  @PostMapping(value="/inspections/post-trip",consumes="application/json")
  DriverShiftContext.Inspection post(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestHeader("Idempotency-Key") UUID key,@RequestBody String body,@RequestParam Map<String,String> query) {
    return inspections.post(actor,tenantId,key,parse(body,query,ShiftContracts.PostTrip.class));
  }
}
