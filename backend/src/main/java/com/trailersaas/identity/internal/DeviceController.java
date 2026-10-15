package com.trailersaas.identity.internal;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/devices")
class DeviceController {
 final UserDeviceService service;DeviceController(UserDeviceService service){this.service=service;}
 @PostMapping UserDeviceService.Registration register(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@RequestBody Map<String,Object> body,@RequestParam Map<String,String> query){if(!query.isEmpty())throw new DeviceFailure(400);return service.register(actor,body);}
 @DeleteMapping("/{deviceId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void deactivate(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID deviceId,@RequestParam Map<String,String> query){if(!query.isEmpty())throw new DeviceFailure(400);service.deactivate(actor,deviceId);}
}
