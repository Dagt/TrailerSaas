package com.trailersaas.documents.internal;
import static com.trailersaas.documents.internal.DocumentContracts.*;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenants/{tenantId}/trailers/{trailerId}/damage-uploads")
class TrailerDamageUploadController {
  private final DocumentService service;
  TrailerDamageUploadController(DocumentService service){this.service=service;}
  @PostMapping(consumes="application/json") @ResponseStatus(HttpStatus.CREATED)
  Upload initialize(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
    @PathVariable UUID trailerId,@RequestBody String body,@RequestParam Map<String,String> query) {
    if(!query.isEmpty()) throw DocumentFailure.invalid();
    return service.initializeTrailer(actor,tenantId,trailerId,DocumentController.request(body,Initialize.class));
  }
}
