CREATE TABLE delivery_evidence (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    trip_stop_id uuid NOT NULL,
    evidence_mode varchar NOT NULL CHECK (evidence_mode IN ('SIGNED_BOL','NO_RECEIVER_AVAILABLE')),
    comment text NULL,
    completed_by_user_id uuid NOT NULL REFERENCES users(id),
    completed_at timestamptz NOT NULL,
    UNIQUE (tenant_id,trip_stop_id),
    FOREIGN KEY (tenant_id,trip_stop_id) REFERENCES trip_stops(tenant_id,id),
    CHECK (comment IS NULL OR comment ~ '[^[:space:]]'),
    CHECK (evidence_mode <> 'NO_RECEIVER_AVAILABLE' OR (comment IS NOT NULL AND comment ~ '[^[:space:]]'))
);
ALTER TABLE delivery_evidence ENABLE ROW LEVEL SECURITY;
CREATE POLICY delivery_evidence_tenant ON delivery_evidence TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON delivery_evidence TO "${runtimeRole}";
-- Explicit context attachments; public Trailer/OOS linking remains a future #49 workflow.
ALTER TABLE document_links DROP CONSTRAINT document_links_entity_type_check;
ALTER TABLE document_links ADD CONSTRAINT document_links_entity_type_check
    CHECK (entity_type IN ('TRIP_STOP','TRIP','TRAILER'));
