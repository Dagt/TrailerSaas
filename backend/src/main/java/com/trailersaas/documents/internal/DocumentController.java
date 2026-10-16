package com.trailersaas.documents.internal;

import static com.trailersaas.documents.internal.DocumentContracts.*;
import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.cfg.EnumFeature;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/documents")
class DocumentController {
  private final DocumentService service;
  private static final JsonMapper STRICT=JsonMapper.builder()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
      .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
  DocumentController(DocumentService service) { this.service=service; }
  static <T> T request(String body,Class<T> type) {
    if(body==null || body.length()>4096) throw DocumentFailure.invalid();
    try { return STRICT.readValue(body,type); }
    catch(RuntimeException e) { throw DocumentFailure.invalid(); }
  }
  private static void noQuery(Map<String,String> query) { if(!query.isEmpty()) throw DocumentFailure.invalid(); }

  @PostMapping(value="/uploads",consumes="application/json") @ResponseStatus(HttpStatus.CREATED)
  Upload upload(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @RequestBody String body,@RequestParam Map<String,String> query) {
    noQuery(query);return service.initialize(actor,tenantId,request(body,Initialize.class));
  }
  @PostMapping(value="/{documentId}/upload-completion",consumes="application/json")
  Metadata complete(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @PathVariable UUID documentId,@RequestHeader("Idempotency-Key") UUID operation,
      @RequestBody String body,@RequestParam Map<String,String> query) {
    noQuery(query);return service.complete(actor,tenantId,documentId,operation,request(body,Complete.class));
  }
  @PostMapping(value="/{documentId}/links",consumes="application/json")
  Link link(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @PathVariable UUID documentId,@RequestBody String body,@RequestParam Map<String,String> query) {
    noQuery(query);return service.link(actor,tenantId,documentId,request(body,LinkRequest.class));
  }
  @GetMapping("/{documentId}")
  Metadata metadata(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @PathVariable UUID documentId,@RequestParam Map<String,String> query) {
    return service.metadata(actor,tenantId,documentId,customer(query));
  }
  @PostMapping("/{documentId}/download-url")
  Download download(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,@PathVariable UUID tenantId,
      @PathVariable UUID documentId,@RequestBody(required=false) String body,@RequestParam Map<String,String> query) {
    UUID customer=customer(query);
    if(body!=null && !body.isBlank() && !body.trim().equals("{}")) throw DocumentFailure.invalid();
    return service.download(actor,tenantId,documentId,customer);
  }
  private static UUID customer(Map<String,String> query) {
    if(!Set.of("customerId").containsAll(query.keySet()))throw DocumentFailure.invalid();
    return query.containsKey("customerId")?CustomerOperationalReads.uuid(query.get("customerId")):null;
  }
}
