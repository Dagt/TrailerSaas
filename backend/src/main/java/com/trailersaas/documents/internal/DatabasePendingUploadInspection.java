package com.trailersaas.documents.internal;

import com.trailersaas.documents.PendingUploadInspection;
import com.trailersaas.identity.TenantContext;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DatabasePendingUploadInspection implements PendingUploadInspection {
  private final TenantContext context;
  private final DocumentRepository repository;
  private final ObjectStoragePort storage;
  DatabasePendingUploadInspection(TenantContext c,DocumentRepository r,ObjectStoragePort s) { context=c;repository=r;storage=s; }
  @Transactional(readOnly=true)
  public List<UUID> candidates(UUID tenant,Instant cutoff,int limit) {
    validate(tenant,cutoff);if(limit<1 || limit>200) throw new IllegalArgumentException("Invalid inspection limit");
    context.establishTenant(tenant);return repository.oldPending(tenant,cutoff,limit);
  }
  @Transactional
  public Optional<Eligibility> inspect(UUID tenant,UUID document,Instant cutoff) {
    validate(tenant,cutoff);context.establishTenant(tenant);
    DocumentRepository.Row row;
    try { row=repository.find(tenant,document,true); }
    catch(DocumentFailure e) { if(e.status==404) return Optional.empty();throw e; }
    if(row.status()!=DocumentContracts.Status.PENDING || !row.created().isBefore(cutoff)
        || !repository.links(tenant,document).isEmpty()) return Optional.empty();
    return Optional.of(new Eligibility(row.id(),row.created(),storage.exists(row.key())));
  }
  private static void validate(UUID tenant,Instant cutoff) {
    if(tenant==null || cutoff==null || cutoff.isAfter(Instant.now())) throw new IllegalArgumentException("Invalid inspection context");
  }
}
