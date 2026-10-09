package com.trailersaas.trailers.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "processed_client_operations")
class ProcessedClientOperationEntity {
  @Id UUID id;

  @Column(name = "tenant_id", nullable = false)
  UUID tenantId;

  @Column(name = "client_operation_id", nullable = false)
  UUID clientOperationId;

  @Column(name = "user_id", nullable = false)
  UUID userId;

  @Column(name = "device_id")
  UUID deviceId;

  @Column(name = "operation_type", nullable = false)
  String operationType;

  @Column(name = "request_hash", nullable = false)
  String requestHash;

  @Column(nullable = false)
  String status;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "processed_at")
  Instant processedAt;

  @Column(name = "result_entity_type")
  String resultEntityType;

  @Column(name = "result_entity_id")
  UUID resultEntityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_data", columnDefinition = "jsonb")
  Map<String, Object> responseData;
}
