package com.trailersaas.documents.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class DocumentContracts {
  enum Type { BOL,POD,DELIVERY_PHOTO,PRETRIP_FRONT,PRETRIP_REAR,PRETRIP_DRIVER_SIDE,PRETRIP_PASSENGER_SIDE,DAMAGE_PHOTO,FUEL_RECEIPT }
  enum Status { PENDING,UPLOADED,FAILED,DELETED_BY_RETENTION }
  enum Source { USER_ACTION,MOBILE_AUTOMATION,SYSTEM_PROCESS,EXTERNAL_INTEGRATION }
  enum Target { TRIP_STOP, TRIP, TRAILER, INSPECTION }
  record Initialize(Type documentType,String contentType,Long sizeBytes,String checksumSha256) {}
  record Complete(String checksumSha256) {}
  record LinkRequest(Target entityType,UUID entityId) {}
  record Upload(UUID documentId,Status uploadStatus,String uploadUrl,Instant expiresAt) {}
  record Link(UUID id,Target entityType,UUID entityId,Instant createdAt) {}
  record Metadata(UUID id,Type documentType,String contentType,Long sizeBytes,String checksumSha256,
      Status uploadStatus,Instant createdAt,Instant uploadedAt,List<Link> links) {}
  record Download(String downloadUrl,Instant expiresAt) {}
}
