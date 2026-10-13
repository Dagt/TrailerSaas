CREATE TABLE "cached_driver_updates" ("user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "driver_id" TEXT NOT NULL, "id" TEXT NOT NULL, "trip_id" TEXT NOT NULL, "update_type" TEXT NOT NULL, "projection_json" TEXT NOT NULL, "sequence" INTEGER NOT NULL, "downloaded_at" INTEGER NOT NULL, PRIMARY KEY ("user_id", "tenant_id", "driver_id", "id"));
CREATE TABLE "cached_stops" ("user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "id" TEXT NOT NULL, "trip_id" TEXT NOT NULL, "sequence_number" INTEGER NOT NULL, "projection_json" TEXT NOT NULL, PRIMARY KEY ("user_id", "tenant_id", "id"));
CREATE TABLE "cached_trailers" ("user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "id" TEXT NOT NULL, "server_version" INTEGER NOT NULL, "projection_json" TEXT NOT NULL, "refreshed_at" INTEGER NOT NULL, PRIMARY KEY ("user_id", "tenant_id", "id"));
CREATE TABLE "cached_trips" ("user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "id" TEXT NOT NULL, "server_version" INTEGER NOT NULL, "status" TEXT NOT NULL, "projection_json" TEXT NOT NULL, "refreshed_at" INTEGER NOT NULL, PRIMARY KEY ("user_id", "tenant_id", "id"));
CREATE TABLE "cached_yard_contexts" ("user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "projection_json" TEXT NOT NULL, "selected_site_id" TEXT NULL, "refreshed_at" INTEGER NOT NULL, "access_blocked" INTEGER NOT NULL DEFAULT 0 CHECK ("access_blocked" IN (0, 1)), PRIMARY KEY ("user_id", "tenant_id"));
CREATE TABLE "camera_captures" ("id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "draft_id" TEXT NOT NULL, "position" TEXT NOT NULL, "media_id" TEXT NULL, "state" TEXT NOT NULL DEFAULT 'PENDING', "created_at" INTEGER NOT NULL, PRIMARY KEY ("id"), FOREIGN KEY(draft_id,user_id,tenant_id) REFERENCES inspection_drafts(id,user_id,tenant_id), FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id), CHECK (position IN ('front','rear','driverSide','passengerSide')), CHECK (state IN ('PENDING','CAPTURED','CANCELLED','FAILED')), CHECK (state<>'CAPTURED' OR media_id IS NOT NULL));
CREATE TABLE "command_media_dependencies" ("client_operation_id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "media_id" TEXT NOT NULL, "field_path" TEXT NOT NULL, PRIMARY KEY ("client_operation_id", "field_path"), UNIQUE(client_operation_id,media_id), FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id), FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id));
CREATE TABLE "command_result_dependencies" ("client_operation_id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "source_operation_id" TEXT NOT NULL, "field_path" TEXT NOT NULL, PRIMARY KEY ("client_operation_id", "field_path"), FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id), FOREIGN KEY(source_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id), CHECK (field_path='shiftId'), CHECK (client_operation_id<>source_operation_id));
CREATE TABLE "command_results" ("client_operation_id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "result_kind" TEXT NOT NULL, "server_id" TEXT NOT NULL, "projection_json" TEXT NOT NULL, "confirmed_at" INTEGER NOT NULL, PRIMARY KEY ("client_operation_id"), FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id), CHECK (result_kind IN ('SHIFT','INSPECTION','TRAILER')));
CREATE TABLE "driver_update_cursors" ("user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "driver_id" TEXT NOT NULL, "cursor" TEXT NULL, "access_blocked" INTEGER NOT NULL DEFAULT 0 CHECK ("access_blocked" IN (0, 1)), PRIMARY KEY ("user_id", "tenant_id", "driver_id"));
CREATE TABLE "inspection_drafts" ("id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "inspection_type" TEXT NOT NULL, "shift_reference_json" TEXT NOT NULL, "tractor_id" TEXT NOT NULL, "trip_id" TEXT NULL, "odometer" TEXT NOT NULL DEFAULT '', "checklist_json" TEXT NOT NULL DEFAULT '{"items":[]}', "photos_json" TEXT NOT NULL DEFAULT '{}', "damages" TEXT NULL, "failures" TEXT NULL, "comments" TEXT NULL, "command_id" TEXT NULL, "updated_at" INTEGER NOT NULL, PRIMARY KEY ("id"), UNIQUE(id,user_id,tenant_id), CHECK (inspection_type IN ('PRE_TRIP','POST_TRIP')), FOREIGN KEY(command_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id));
CREATE TABLE "local_commands" ("client_operation_id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "command_type" TEXT NOT NULL, "queue_sequence" INTEGER NOT NULL, "endpoint_path" TEXT NOT NULL, "http_method" TEXT NOT NULL, "payload_json" TEXT NOT NULL, "trip_id" TEXT NULL, "aggregate_id" TEXT NULL, "aggregate_type" TEXT NOT NULL DEFAULT 'TRIP', "resource_keys_json" TEXT NOT NULL, "dependencies_json" TEXT NOT NULL, "occurred_at" INTEGER NOT NULL, "created_at" INTEGER NOT NULL, "state" TEXT NOT NULL, "attempt_count" INTEGER NOT NULL DEFAULT 0, "next_retry_at" INTEGER NULL, "last_http_status" INTEGER NULL, "last_error_code" TEXT NULL, "acked_at" INTEGER NULL, PRIMARY KEY ("client_operation_id"), CHECK (state IN ('QUEUED','SENDING','ACKNOWLEDGED','RETRYABLE_FAILURE','CONFLICT','BLOCKED','PERMANENT_FAILURE')), CHECK (attempt_count >= 0), CHECK (aggregate_type IN ('TRIP','SHIFT','INSPECTION','TRAILER')), CHECK (aggregate_type <> 'TRIP' OR (trip_id IS NOT NULL AND aggregate_id IS NOT NULL)), CHECK (aggregate_type <> 'TRAILER' OR (trip_id IS NULL AND aggregate_id IS NOT NULL)), UNIQUE (user_id,tenant_id,queue_sequence), CHECK (http_method = 'POST'));
CREATE TABLE "local_media" ("id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "document_type" TEXT NOT NULL, "content_type" TEXT NOT NULL, "file_key" TEXT NOT NULL, "size_bytes" INTEGER NOT NULL, "checksum_sha256" TEXT NOT NULL, "state" TEXT NOT NULL, "server_document_id" TEXT NULL, "finalize_key" TEXT NOT NULL, "attempt_history_json" TEXT NOT NULL DEFAULT '[]', "attempt_count" INTEGER NOT NULL DEFAULT 0, "next_retry_at" INTEGER NULL, "last_http_status" INTEGER NULL, "last_error_code" TEXT NULL, "review_required" INTEGER NOT NULL DEFAULT 0 CHECK ("review_required" IN (0, 1)), "trip_id" TEXT NULL, "captured_at" INTEGER NOT NULL, "updated_at" INTEGER NOT NULL, "finalized_at" INTEGER NULL, "file_state" TEXT NOT NULL DEFAULT 'PRESENT', "retain_offline" INTEGER NOT NULL DEFAULT 1 CHECK ("retain_offline" IN (0, 1)), PRIMARY KEY ("id"), UNIQUE(id,user_id,tenant_id), UNIQUE(user_id,tenant_id,server_document_id), CHECK(file_key=id), CHECK(length(finalize_key)=36 AND finalize_key GLOB '????????-????-????-????-????????????' AND finalize_key NOT GLOB '*[^0-9a-f-]*'), CHECK(server_document_id IS NULL OR (length(server_document_id)=36 AND server_document_id GLOB '????????-????-????-????-????????????' AND server_document_id NOT GLOB '*[^0-9a-f-]*')), CHECK(length(id)=36 AND id GLOB '????????-????-????-????-????????????' AND id NOT GLOB '*[^0-9a-f-]*'), CHECK(size_bytes > 0 AND size_bytes <= 104857600), CHECK(length(checksum_sha256)=64), CHECK(checksum_sha256 NOT GLOB '*[^0-9a-f]*'), CHECK(document_type IN ('BOL','POD','DELIVERY_PHOTO','PRETRIP_FRONT','PRETRIP_REAR','PRETRIP_DRIVER_SIDE','PRETRIP_PASSENGER_SIDE','DAMAGE_PHOTO','FUEL_RECEIPT')), CHECK(content_type IN ('image/jpeg','image/png','application/pdf')), CHECK(content_type<>'application/pdf' OR document_type IN ('BOL','POD')), CHECK(state IN ('LOCAL_ONLY','INIT_PENDING','UPLOAD_PENDING','UPLOADING','UPLOADED','FINALIZE_PENDING','FINALIZED','FAILED')), CHECK(file_state IN ('PRESENT','REMOVING','REMOVED')), CHECK(attempt_count >= 0), CHECK(state <> 'FINALIZED' OR (server_document_id IS NOT NULL AND finalized_at IS NOT NULL)));
CREATE TABLE "local_session_context" ("firebase_uid" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "membership_json" TEXT NOT NULL, "driver_json" TEXT NULL, "refreshed_at" INTEGER NOT NULL, PRIMARY KEY ("user_id", "tenant_id"));
CREATE TABLE "local_sync_issues" ("id" TEXT NOT NULL, "client_operation_id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "category" TEXT NOT NULL, "error_code" TEXT NOT NULL, "related_resource" TEXT NOT NULL, "message" TEXT NOT NULL, "created_at" INTEGER NOT NULL, "resolved_at" INTEGER NULL, PRIMARY KEY ("id"));
CREATE TABLE "materialized_requests" ("client_operation_id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "endpoint_path" TEXT NOT NULL, "http_method" TEXT NOT NULL, "payload_json" TEXT NOT NULL, "created_at" INTEGER NOT NULL, PRIMARY KEY ("client_operation_id"), FOREIGN KEY(client_operation_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id), CHECK(http_method='POST'));
CREATE TABLE "road_captures" ("id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "draft_id" TEXT NOT NULL, "document_type" TEXT NOT NULL, "field" TEXT NOT NULL, "media_id" TEXT NULL, "state" TEXT NOT NULL DEFAULT 'PENDING', "created_at" INTEGER NOT NULL, PRIMARY KEY ("id"), FOREIGN KEY(draft_id,user_id,tenant_id) REFERENCES road_drafts(id,user_id,tenant_id), FOREIGN KEY(media_id,user_id,tenant_id) REFERENCES local_media(id,user_id,tenant_id), CHECK (document_type IN ('BOL','DELIVERY_PHOTO','FUEL_RECEIPT','DAMAGE_PHOTO')), CHECK (state IN ('PENDING','CAPTURED','CANCELLED','FAILED')), CHECK (state<>'CAPTURED' OR media_id IS NOT NULL));
CREATE TABLE "road_drafts" ("id" TEXT NOT NULL, "user_id" TEXT NOT NULL, "tenant_id" TEXT NOT NULL, "trip_id" TEXT NOT NULL, "tractor_id" TEXT NULL, "stop_id" TEXT NULL, "purpose" TEXT NOT NULL, "inputs_json" TEXT NOT NULL DEFAULT '{}', "media_json" TEXT NOT NULL DEFAULT '{}', "command_id" TEXT NULL, "updated_at" INTEGER NOT NULL, PRIMARY KEY ("id"), UNIQUE(id,user_id,tenant_id), CHECK (purpose IN ('SIGNED_BOL','NO_RECEIVER_AVAILABLE','FUEL','PROBLEM')), CHECK (purpose<>'FUEL' OR tractor_id IS NOT NULL), FOREIGN KEY(command_id,user_id,tenant_id) REFERENCES local_commands(client_operation_id,user_id,tenant_id));
CREATE UNIQUE INDEX camera_one_pending ON camera_captures(state) WHERE state='PENDING';
CREATE INDEX driver_updates_scope_order ON cached_driver_updates(user_id,tenant_id,driver_id,sequence);
CREATE INDEX inspection_drafts_scope ON inspection_drafts(user_id,tenant_id,updated_at);
CREATE UNIQUE INDEX local_commands_media_fk ON local_commands(client_operation_id,user_id,tenant_id);
CREATE INDEX local_commands_scope_state ON local_commands(user_id, tenant_id, state, created_at);
CREATE INDEX local_media_scope_retry ON local_media(user_id,tenant_id,state,next_retry_at);
CREATE INDEX local_sync_issues_scope ON local_sync_issues(user_id, tenant_id, created_at);
CREATE UNIQUE INDEX road_camera_one_pending ON road_captures(state) WHERE state='PENDING';
CREATE INDEX road_drafts_scope ON road_drafts(user_id,tenant_id,updated_at);
CREATE TRIGGER camera_capture_identity_immutable BEFORE UPDATE OF id,user_id,tenant_id,draft_id,position,created_at ON camera_captures BEGIN SELECT RAISE(ABORT,'Capture context is immutable');END;
CREATE TRIGGER capture_before_queue BEFORE INSERT ON camera_captures
      WHEN NOT EXISTS(SELECT 1 FROM inspection_drafts d WHERE d.id=NEW.draft_id AND d.user_id=NEW.user_id AND d.tenant_id=NEW.tenant_id AND d.command_id IS NULL AND d.inspection_type='PRE_TRIP')
      BEGIN SELECT RAISE(ABORT,'Only an editable Pre-Trip can capture');END;
CREATE TRIGGER command_media_dependencies_delete_immutable BEFORE DELETE ON command_media_dependencies BEGIN SELECT RAISE(ABORT, 'Materialized intent is immutable'); END;
CREATE TRIGGER command_media_dependencies_update_immutable BEFORE UPDATE ON command_media_dependencies BEGIN SELECT RAISE(ABORT, 'Materialized intent is immutable'); END;
CREATE TRIGGER command_result_dependencies_delete_immutable BEFORE DELETE ON command_result_dependencies BEGIN SELECT RAISE(ABORT,'Confirmed identity and dependencies are immutable'); END;
CREATE TRIGGER command_result_dependencies_update_immutable BEFORE UPDATE ON command_result_dependencies BEGIN SELECT RAISE(ABORT,'Confirmed identity and dependencies are immutable'); END;
CREATE TRIGGER command_results_delete_immutable BEFORE DELETE ON command_results BEGIN SELECT RAISE(ABORT,'Confirmed identity and dependencies are immutable'); END;
CREATE TRIGGER command_results_update_immutable BEFORE UPDATE ON command_results BEGIN SELECT RAISE(ABORT,'Confirmed identity and dependencies are immutable'); END;
CREATE TRIGGER inspection_camera_exclusive BEFORE INSERT ON camera_captures WHEN NEW.state='PENDING' AND EXISTS(SELECT 1 FROM road_captures WHERE state='PENDING') BEGIN SELECT RAISE(ABORT,'Camera still active');END;
CREATE TRIGGER inspection_draft_identity_immutable BEFORE UPDATE OF id,user_id,tenant_id,inspection_type,shift_reference_json,tractor_id,trip_id ON inspection_drafts BEGIN SELECT RAISE(ABORT,'Draft context is immutable');END;
CREATE TRIGGER inspection_draft_queued_immutable BEFORE UPDATE ON inspection_drafts WHEN OLD.command_id IS NOT NULL BEGIN SELECT RAISE(ABORT,'Queued draft is immutable');END;
CREATE TRIGGER local_command_intent_immutable
      BEFORE UPDATE OF client_operation_id,user_id,tenant_id,command_type,
      endpoint_path,http_method,payload_json,trip_id,aggregate_id,aggregate_type,
      resource_keys_json,dependencies_json,occurred_at,created_at,queue_sequence ON local_commands
      BEGIN SELECT RAISE(ABORT, 'Command intent is immutable'); END;
CREATE TRIGGER local_media_attempt_identity_guard
      BEFORE UPDATE OF server_document_id,finalize_key ON local_media
      WHEN (OLD.server_document_id IS NOT NULL AND
          (NEW.server_document_id IS NOT OLD.server_document_id OR NEW.finalize_key<>OLD.finalize_key))
        OR (OLD.server_document_id IS NULL AND NEW.finalize_key<>OLD.finalize_key)
      BEGIN
        SELECT CASE WHEN NOT (OLD.server_document_id IS NOT NULL AND OLD.last_error_code='UPLOAD_NOT_COMPLETE'
          AND NEW.server_document_id IS NULL AND NEW.state='INIT_PENDING')
          THEN RAISE(ABORT, 'Uncertain upload identity is immutable') END;
      END;
CREATE TRIGGER local_media_content_immutable
      BEFORE UPDATE OF id,user_id,tenant_id,document_type,content_type,file_key,size_bytes,checksum_sha256,captured_at,trip_id ON local_media
      BEGIN SELECT RAISE(ABORT, 'Media content is immutable'); END;
CREATE TRIGGER local_media_final_identity_immutable
      BEFORE UPDATE OF server_document_id,finalize_key,finalized_at,state ON local_media
      WHEN OLD.state='FINALIZED'
      BEGIN SELECT RAISE(ABORT, 'Finalized media is immutable'); END;
CREATE TRIGGER materialized_request_matches_intent BEFORE INSERT ON materialized_requests
      WHEN NOT EXISTS(SELECT 1 FROM local_commands c WHERE c.client_operation_id=NEW.client_operation_id
        AND c.user_id=NEW.user_id AND c.tenant_id=NEW.tenant_id AND c.http_method=NEW.http_method
        AND (c.endpoint_path=NEW.endpoint_path OR (c.command_type='off-duty' AND c.aggregate_type='SHIFT'
          AND c.endpoint_path='tenants/'||c.tenant_id||'/driver/me/shifts/{shiftId}/off-duty'
          AND EXISTS(SELECT 1 FROM command_result_dependencies d JOIN command_results r ON r.client_operation_id=d.source_operation_id
            JOIN local_commands source ON source.client_operation_id=r.client_operation_id
            WHERE d.client_operation_id=c.client_operation_id AND d.user_id=c.user_id AND d.tenant_id=c.tenant_id
              AND r.user_id=c.user_id AND r.tenant_id=c.tenant_id AND r.result_kind='SHIFT' AND source.state='ACKNOWLEDGED'
              AND NEW.endpoint_path='tenants/'||c.tenant_id||'/driver/me/shifts/'||r.server_id||'/off-duty'))))
      BEGIN SELECT RAISE(ABORT,'Materialized request must match typed original intent'); END;
CREATE TRIGGER materialized_requests_delete_immutable BEFORE DELETE ON materialized_requests BEGIN SELECT RAISE(ABORT, 'Materialized intent is immutable'); END;
CREATE TRIGGER materialized_requests_update_immutable BEFORE UPDATE ON materialized_requests BEGIN SELECT RAISE(ABORT, 'Materialized intent is immutable'); END;
CREATE TRIGGER media_dependency_before_send
      BEFORE INSERT ON command_media_dependencies
      WHEN EXISTS(SELECT 1 FROM materialized_requests WHERE client_operation_id=NEW.client_operation_id)
        OR EXISTS(SELECT 1 FROM local_commands WHERE client_operation_id=NEW.client_operation_id AND (attempt_count>0 OR state<>'QUEUED'))
        OR EXISTS(SELECT 1 FROM local_media WHERE id=NEW.media_id AND file_state<>'PRESENT')
      BEGIN SELECT RAISE(ABORT, 'Dependencies must precede sending'); END;
CREATE TRIGGER queue_after_capture BEFORE UPDATE OF command_id ON inspection_drafts
      WHEN NEW.command_id IS NOT NULL AND EXISTS(SELECT 1 FROM camera_captures c WHERE c.draft_id=OLD.id AND c.state='PENDING')
      BEGIN SELECT RAISE(ABORT,'Resolve camera before queueing');END;
CREATE TRIGGER result_dependency_before_send BEFORE INSERT ON command_result_dependencies
      WHEN EXISTS(SELECT 1 FROM materialized_requests WHERE client_operation_id=NEW.client_operation_id)
        OR NOT EXISTS(SELECT 1 FROM local_commands c JOIN local_commands source ON source.client_operation_id=NEW.source_operation_id
          WHERE c.client_operation_id=NEW.client_operation_id AND c.user_id=NEW.user_id AND c.tenant_id=NEW.tenant_id
            AND c.state='QUEUED' AND c.attempt_count=0 AND source.user_id=c.user_id AND source.tenant_id=c.tenant_id
            AND source.command_type='on-duty' AND source.queue_sequence<c.queue_sequence)
      BEGIN SELECT RAISE(ABORT,'Generated identity dependency must precede sending'); END;
CREATE TRIGGER road_camera_exclusive BEFORE INSERT ON road_captures WHEN NEW.state='PENDING' AND EXISTS(SELECT 1 FROM camera_captures WHERE state='PENDING') BEGIN SELECT RAISE(ABORT,'Camera still active');END;
CREATE TRIGGER road_capture_before_queue BEFORE INSERT ON road_captures WHEN EXISTS(SELECT 1 FROM road_drafts d WHERE d.id=NEW.draft_id AND d.command_id IS NOT NULL) BEGIN SELECT RAISE(ABORT,'Draft already queued');END;
CREATE TRIGGER road_capture_context_immutable BEFORE UPDATE OF id,user_id,tenant_id,draft_id,document_type,field,created_at ON road_captures BEGIN SELECT RAISE(ABORT,'Capture context is immutable');END;
CREATE TRIGGER road_draft_context_immutable BEFORE UPDATE OF id,user_id,tenant_id,trip_id,tractor_id,stop_id,purpose ON road_drafts BEGIN SELECT RAISE(ABORT,'Draft context is immutable');END;
CREATE TRIGGER road_draft_queued_immutable BEFORE UPDATE ON road_drafts WHEN OLD.command_id IS NOT NULL BEGIN SELECT RAISE(ABORT,'Queued draft is immutable');END;
CREATE TRIGGER road_queue_after_capture BEFORE UPDATE OF command_id ON road_drafts WHEN NEW.command_id IS NOT NULL AND EXISTS(SELECT 1 FROM road_captures c WHERE c.draft_id=OLD.id AND c.state='PENDING') BEGIN SELECT RAISE(ABORT,'Capture still pending');END;
PRAGMA user_version=7;
