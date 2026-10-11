package com.trailersaas.documents.internal;

import static com.trailersaas.documents.internal.DocumentContracts.*;
import com.trailersaas.drivers.DriverIdentityPolicy;
import com.trailersaas.identity.*;
import com.trailersaas.trips.DocumentStopDirectory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DocumentService {
  private final DocumentRepository repository;
  private final ObjectStoragePort storage;
  private final R2Configuration.Settings settings;
  private final TenantAuthorization authorization;
  private final DocumentAccessPolicy access;
  private final DriverIdentityPolicy drivers;
  private final DocumentStopDirectory stops;
  private final MobileOperationLedger ledger;
  DocumentService(DocumentRepository r,ObjectStoragePort s,R2Configuration.Settings c,TenantAuthorization a,
      DocumentAccessPolicy p,DriverIdentityPolicy d,DocumentStopDirectory t,MobileOperationLedger l) {
    repository=r;storage=s;settings=c;authorization=a;access=p;drivers=d;stops=t;ledger=l;
  }

  @Transactional
  Upload initialize(AuthenticatedUserPrincipal actor,UUID tenant,Initialize request) {
    if(request==null || request.documentType()==null || request.contentType()==null || request.sizeBytes()==null
        || request.sizeBytes()<1 || request.sizeBytes()>settings.maxBytes()) throw DocumentFailure.invalid();
    String mime=request.contentType();
    if(!settings.allowedTypes().contains(mime) || !(Set.of("image/jpeg","image/png").contains(mime)
        || (mime.equals("application/pdf") && Set.of(Type.BOL,Type.POD).contains(request.documentType())))) throw DocumentFailure.invalid();
    String sha=checksum(request.checksumSha256());
    uploadAuthority(actor,tenant,true);
    if(!Set.of(Type.BOL,Type.POD,Type.DELIVERY_PHOTO).contains(request.documentType())) throw denied();
    UUID id=UUID.randomUUID(); Instant time=Instant.now(); String key=temporary(tenant,id);
    var signed=storage.put(key,mime,request.sizeBytes(),settings.uploadTtl());
    repository.insert(new DocumentRepository.Row(id,tenant,key,request.documentType(),mime,request.sizeBytes(),sha,
        Status.PENDING,actor.userId(),Source.USER_ACTION,time,null));
    return new Upload(id,Status.PENDING,signed.url(),signed.expiresAt());
  }

  @Transactional
  Metadata complete(AuthenticatedUserPrincipal actor,UUID tenant,UUID document,UUID operation,Complete request) {
    if(request==null || operation==null) throw DocumentFailure.invalid();
    String supplied=checksum(request.checksumSha256());
    uploadAuthority(actor,tenant,true);
    var row=repository.find(tenant,document,true);
    if(!actor.userId().equals(row.uploader())) throw DocumentFailure.missing();
    if(row.status()!=Status.PENDING && row.status()!=Status.UPLOADED) throw DocumentFailure.conflict("RESOURCE_CONFLICT");
    var linked=repository.links(tenant,document);
    // A saved completion result contains metadata too: authorize linked resources before ledger replay.
    if(!linked.isEmpty() && visibleLinks(actor,tenant,linked,access.readScope(actor,tenant)).isEmpty())
      throw DocumentFailure.missing();
    String hash=HexFormat.of().formatHex(R2ObjectStorage.sha256().digest((document+"|"+Objects.toString(supplied,"null")).getBytes(StandardCharsets.UTF_8)));
    return ledger.execute(tenant,actor.userId(),operation,"DOCUMENT_UPLOAD_COMPLETION",hash,"DOCUMENT",Metadata::id,Metadata.class,()-> {
      if(row.status()==Status.UPLOADED) {
        if(supplied!=null && !supplied.equals(row.checksum())) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
        return DocumentRepository.metadata(row,List.of());
      }
      if(supplied!=null && row.checksum()!=null && !supplied.equals(row.checksum())) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
      String publication="tenant/"+tenant+"/documents/"+document+"/"+UUID.randomUUID();
      var verified=storage.copyAndVerify(row.key(),publication,row.size());
      if(verified.size()!=row.size() || !row.contentType().equals(verified.contentType())
          || (row.checksum()!=null && !row.checksum().equals(verified.sha256()))
          || (supplied!=null && !supplied.equals(verified.sha256()))) throw DocumentFailure.conflict("UPLOAD_CONTENT_MISMATCH");
      repository.publish(row,publication,verified.sha256(),Instant.now());
      return DocumentRepository.metadata(repository.find(tenant,document,false),List.of());
    });
  }

  @Transactional
  Link link(AuthenticatedUserPrincipal actor,UUID tenant,UUID document,LinkRequest request) {
    if(request==null || request.entityType()!=Target.TRIP_STOP || request.entityId()==null) throw DocumentFailure.invalid();
    // Parent Trip precedes Driver and Document locks, matching operational assignment/execution order.
    authorization.requireTenantCapability(actor,tenant,Capability.POD_UPLOAD);
    var stop=stops.find(tenant,request.entityId(),true).orElseThrow(DocumentFailure::missing);
    UUID driver=uploadAuthority(actor,tenant,true);
    if(!driver.equals(stop.driverId())) throw DocumentFailure.missing();
    authorization.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,true);
    var row=repository.find(tenant,document,true);
    if(!actor.userId().equals(row.uploader())) throw DocumentFailure.missing();
    if(row.status()!=Status.UPLOADED) throw DocumentFailure.conflict("UPLOAD_NOT_COMPLETE");
    if(!Set.of(Type.BOL,Type.POD,Type.DELIVERY_PHOTO).contains(row.type())) throw denied();
    var existing=repository.links(tenant,document);
    if(existing.size()>=50 && existing.stream().noneMatch(l->l.entityId().equals(request.entityId())))
      throw DocumentFailure.conflict("RESOURCE_CONFLICT");
    repository.link(tenant,document,request);
    return repository.links(tenant,document).stream().filter(l->l.entityId().equals(request.entityId())).findFirst().orElseThrow();
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  Metadata metadata(AuthenticatedUserPrincipal actor,UUID tenant,UUID document) {
    var scope=access.readScope(actor,tenant);
    var row=repository.find(tenant,document,false);
    var linked=repository.links(tenant,document);
    var visible=visibleLinks(actor,tenant,linked,scope);
    // Authorship allows managing an unlinked upload, never bypassing authorization of existing links.
    boolean managing=!scope.tenantWide() && linked.isEmpty() && scope.assignedWork() && actor.userId().equals(row.uploader());
    if(managing) uploadAuthority(actor,tenant,false);
    if(!scope.tenantWide() && visible.isEmpty() && !managing) throw DocumentFailure.missing();
    return DocumentRepository.metadata(row,visible);
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  Download download(AuthenticatedUserPrincipal actor,UUID tenant,UUID document) {
    var scope=access.readScope(actor,tenant);
    var row=repository.find(tenant,document,false);
    if(visibleLinks(actor,tenant,repository.links(tenant,document),scope).isEmpty()) throw DocumentFailure.missing();
    if(row.status()!=Status.UPLOADED) throw DocumentFailure.conflict("UPLOAD_NOT_COMPLETE");
    if(!storage.exists(row.key())) throw DocumentFailure.storage();
    var signed=storage.get(row.key(),settings.downloadTtl());return new Download(signed.url(),signed.expiresAt());
  }

  private List<Link> visibleLinks(AuthenticatedUserPrincipal actor,UUID tenant,List<Link> linked,DocumentAccessPolicy.ReadScope scope) {
    UUID driver=scope.assignedWork()?drivers.activeForUser(tenant,actor.userId()).orElse(null):null;
    var visible=linked.stream().filter(link-> {
      var stop=stops.find(tenant,link.entityId(),false);
      if(stop.isEmpty()) return false;
      return scope.tenantWide()
          || (!stop.get().customerIds().isEmpty() && scope.customerIds().containsAll(stop.get().customerIds()))
          || (driver!=null && driver.equals(stop.get().driverId()));
    }).toList();
    // A document may span several Stops. One visible link must not disclose other work's bytes.
    return !scope.tenantWide() && visible.size()!=linked.size()?List.of():visible;
  }
  private UUID uploadAuthority(AuthenticatedUserPrincipal actor,UUID tenant,boolean lock) {
    authorization.requireTenantCapability(actor,tenant,Capability.POD_UPLOAD);
    return (lock?drivers.lockActiveForUser(tenant,actor.userId()):drivers.activeForUser(tenant,actor.userId())).orElseThrow(DocumentService::denied);
  }
  static String checksum(String value) {
    if(value==null) return null;
    if(!value.matches("[0-9a-fA-F]{64}")) throw DocumentFailure.invalid();return value.toLowerCase(Locale.ROOT);
  }
  static String temporary(UUID tenant,UUID document) { return "tenant/"+tenant+"/uploads/"+document; }
  private static DocumentFailure denied() { return new DocumentFailure(403,"CAPABILITY_DENIED","The requested action is not permitted."); }
}
