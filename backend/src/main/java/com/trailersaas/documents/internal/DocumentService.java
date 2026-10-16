package com.trailersaas.documents.internal;

import static com.trailersaas.documents.internal.DocumentContracts.*;
import com.trailersaas.identity.*;
import com.trailersaas.trips.DocumentStopDirectory;
import com.trailersaas.trips.EvidenceWorkDirectory;
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
  private final DocumentUploadAuthority uploads;
  private final DocumentResourceAccess resources;
  private final DocumentStopDirectory stops;
  private final EvidenceWorkDirectory work;
  private final MobileOperationLedger ledger;
  private final com.trailersaas.trailers.TrailerEvidenceDirectory trailers;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final CustomerOperationalReads customerReads;
  DocumentService(DocumentRepository r,ObjectStoragePort s,R2Configuration.Settings c,TenantAuthorization a,
      DocumentAccessPolicy p,DocumentUploadAuthority d,DocumentStopDirectory t,MobileOperationLedger l,
      DocumentResourceAccess v,EvidenceWorkDirectory w,com.trailersaas.trailers.TrailerEvidenceDirectory trailers,org.springframework.jdbc.core.JdbcTemplate jdbc,CustomerOperationalReads customerReads) {
    repository=r;storage=s;settings=c;authorization=a;access=p;uploads=d;stops=t;ledger=l;resources=v;work=w;this.trailers=trailers;this.jdbc=jdbc;this.customerReads=customerReads;
  }

  @Transactional
  Upload initialize(AuthenticatedUserPrincipal actor,UUID tenant,Initialize request) {
    if(request==null || request.documentType()==null) throw DocumentFailure.invalid();
    uploads.require(actor,tenant,request.documentType(),true);
    return createUpload(actor,tenant,request);
  }

  @Transactional
  Upload initializeTrailer(AuthenticatedUserPrincipal actor,UUID tenant,UUID trailer,Initialize request) {
    if(request==null || request.documentType()!=Type.DAMAGE_PHOTO) throw DocumentFailure.invalid();
    trailerAuthority(actor,tenant,trailer,true);
    var upload=createUpload(actor,tenant,request);
    repository.link(tenant,upload.documentId(),new LinkRequest(Target.TRAILER,trailer));
    return upload;
  }

  private Upload createUpload(AuthenticatedUserPrincipal actor,UUID tenant,Initialize request) {
    if(request==null || request.documentType()==null || request.contentType()==null || request.sizeBytes()==null
        || request.sizeBytes()<1 || request.sizeBytes()>settings.maxBytes()) throw DocumentFailure.invalid();
    String mime=request.contentType();
    if(!settings.allowedTypes().contains(mime) || !(Set.of("image/jpeg","image/png").contains(mime)
        || (mime.equals("application/pdf") && Set.of(Type.BOL,Type.POD).contains(request.documentType())))) throw DocumentFailure.invalid();
    String sha=checksum(request.checksumSha256());
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
    access.readScope(actor,tenant);
    var identity=repository.find(tenant,document,false);
    var trailerContext=trailerContext(identity,repository.links(tenant,document));
    if(trailerContext!=null) trailerAuthority(actor,tenant,trailerContext,true);
    else uploads.require(actor,tenant,identity.type(),true);
    var row=repository.find(tenant,document,true);
    if(trailerContext!=null) trailerAuthority(actor,tenant,trailerContext,false);
    else uploads.require(actor,tenant,row.type(),false);
    if(!actor.userId().equals(row.uploader())) throw DocumentFailure.missing();
    if(row.status()!=Status.PENDING && row.status()!=Status.UPLOADED) throw DocumentFailure.conflict("RESOURCE_CONFLICT");
    var linked=repository.links(tenant,document);
    // A saved completion result contains metadata too: authorize linked resources before ledger replay.
    if(!linked.isEmpty() && resources.visible(actor,tenant,linked,access.readScope(actor,tenant)).isEmpty())
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
    if(request==null || request.entityType()==null || request.entityType()==Target.TRAILER || request.entityType()==Target.INSPECTION || request.entityId()==null) throw DocumentFailure.invalid();
    // Parent Trip precedes Driver and Document locks, matching operational assignment/execution order.
    access.readScope(actor,tenant);
    var identity=repository.find(tenant,document,false);
    authorization.requireTenantCapability(actor,tenant,DocumentUploadAuthority.capability(identity.type()));
    if(repository.links(tenant,document).stream().anyMatch(l->l.entityType()==Target.TRAILER)) throw DocumentFailure.invalid();
    UUID assigned;
    if(request.entityType()==Target.TRIP_STOP) {
      if(!Set.of(Type.BOL,Type.POD,Type.DELIVERY_PHOTO,Type.DAMAGE_PHOTO).contains(identity.type())) throw DocumentFailure.invalid();
      assigned=stops.find(tenant,request.entityId(),true).orElseThrow(DocumentFailure::missing).driverId();
    } else {
      if(Set.of(Type.BOL,Type.POD,Type.DELIVERY_PHOTO).contains(identity.type())) throw DocumentFailure.invalid();
      var parent=work.trip(tenant,request.entityId(),false).orElseThrow(DocumentFailure::missing);
      if(Set.of("COMPLETED","CANCELLED").contains(parent.status())) throw DocumentFailure.conflict("INVALID_STATE_TRANSITION");
      assigned=parent.driverId();
    }
    UUID driver=uploads.require(actor,tenant,identity.type(),true);
    if(!driver.equals(assigned)) throw DocumentFailure.missing();
    authorization.requireAssignedWorkCapability(actor,tenant,Capability.TRIP_VIEW_ASSIGNED,true);
    var row=repository.find(tenant,document,true);
    uploads.require(actor,tenant,row.type(),false);
    if(!actor.userId().equals(row.uploader())) throw DocumentFailure.missing();
    if(row.status()!=Status.UPLOADED) throw DocumentFailure.conflict("UPLOAD_NOT_COMPLETE");
    var existing=repository.links(tenant,document);
    if(existing.size()>=50 && existing.stream().noneMatch(l->l.entityType()==request.entityType() && l.entityId().equals(request.entityId())))
      throw DocumentFailure.conflict("RESOURCE_CONFLICT");
    repository.link(tenant,document,request);
    return repository.links(tenant,document).stream().filter(l->l.entityType()==request.entityType() && l.entityId().equals(request.entityId())).findFirst().orElseThrow();
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  Metadata metadata(AuthenticatedUserPrincipal actor,UUID tenant,UUID document) {
    return metadata(actor,tenant,document,null);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  Metadata metadata(AuthenticatedUserPrincipal actor,UUID tenant,UUID document,UUID customer) {
    var scope=customerScope(actor,tenant,document,customer);
    var row=repository.find(tenant,document,false);
    var linked=repository.links(tenant,document);
    var visible=resources.visible(actor,tenant,linked,scope);
    // Authorship allows managing an unlinked upload, never bypassing authorization of existing links.
    boolean managing=!scope.tenantWide() && linked.isEmpty() && scope.assignedWork() && actor.userId().equals(row.uploader());
    if(managing) uploads.require(actor,tenant,row.type(),false);
    if(!scope.tenantWide() && visible.isEmpty() && !managing) throw DocumentFailure.missing();
    return DocumentRepository.metadata(row,visible);
  }

  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  Download download(AuthenticatedUserPrincipal actor,UUID tenant,UUID document) {
    return download(actor,tenant,document,null);
  }
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  Download download(AuthenticatedUserPrincipal actor,UUID tenant,UUID document,UUID customer) {
    var scope=customerScope(actor,tenant,document,customer);
    var row=repository.find(tenant,document,false);
    if(resources.visible(actor,tenant,repository.links(tenant,document),scope).isEmpty()) throw DocumentFailure.missing();
    if(row.status()!=Status.UPLOADED) throw DocumentFailure.conflict("UPLOAD_NOT_COMPLETE");
    if(!storage.exists(row.key())) throw DocumentFailure.storage();
    var signed=storage.get(row.key(),settings.downloadTtl());return new Download(signed.url(),signed.expiresAt());
  }
  private DocumentAccessPolicy.ReadScope customerScope(AuthenticatedUserPrincipal actor,UUID tenant,UUID document,UUID customer) {
    var scope=access.readScope(actor,tenant);
    if(customer==null)return scope;
    customerReads.documentAuthority(actor,tenant,customer);
    if(!customerReads.containsDocument(tenant,customer,document))throw DocumentFailure.missing();
    return new DocumentAccessPolicy.ReadScope(false,Set.of(customer),false);
  }

  private UUID trailerContext(DocumentRepository.Row row,List<Link> links) {
    if(row.type()!=Type.DAMAGE_PHOTO || links.size()!=1 || links.getFirst().entityType()!=Target.TRAILER) return null;
    return links.getFirst().entityId();
  }
  private void trailerAuthority(AuthenticatedUserPrincipal actor,UUID tenant,UUID trailer,boolean lock) {
    authorization.requireTenantCapability(actor,tenant,Capability.TRAILER_OOS_MARK);
    if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from tenants where id=? and status='ACTIVE')",Boolean.class,tenant))) throw new com.trailersaas.documents.EvidenceValidationException(com.trailersaas.documents.EvidenceValidationException.Code.CAPABILITY_DENIED);
    if(!(lock?trailers.lockExists(tenant,trailer):trailers.exists(tenant,trailer))
       || !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from trailers where tenant_id=? and id=? and active)",Boolean.class,tenant,trailer))) throw DocumentFailure.missing();
    authorization.requireTenantCapability(actor,tenant,Capability.TRAILER_OOS_MARK);
  }

  static String checksum(String value) {
    if(value==null) return null;
    if(!value.matches("[0-9a-fA-F]{64}")) throw DocumentFailure.invalid();return value.toLowerCase(Locale.ROOT);
  }
  static String temporary(UUID tenant,UUID document) { return "tenant/"+tenant+"/uploads/"+document; }
}
