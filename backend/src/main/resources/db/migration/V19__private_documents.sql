CREATE TABLE documents (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    object_key varchar NOT NULL,
    document_type varchar NOT NULL CHECK (document_type IN ('BOL','POD','DELIVERY_PHOTO','PRETRIP_FRONT','PRETRIP_REAR','PRETRIP_DRIVER_SIDE','PRETRIP_PASSENGER_SIDE','DAMAGE_PHOTO','FUEL_RECEIPT')),
    content_type varchar NULL,
    size_bytes bigint NULL CHECK (size_bytes IS NULL OR size_bytes > 0),
    checksum_sha256 varchar NULL CHECK (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[0-9a-f]{64}$'),
    upload_status varchar NOT NULL CHECK (upload_status IN ('PENDING','UPLOADED','FAILED','DELETED_BY_RETENTION')),
    uploaded_by_user_id uuid NULL REFERENCES users(id),
    source_type varchar NOT NULL CHECK (source_type IN ('USER_ACTION','MOBILE_AUTOMATION','SYSTEM_PROCESS','EXTERNAL_INTEGRATION')),
    created_at timestamptz NOT NULL,
    uploaded_at timestamptz NULL,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,object_key),
    CHECK (upload_status <> 'UPLOADED' OR (uploaded_at IS NOT NULL AND content_type IS NOT NULL AND size_bytes IS NOT NULL AND checksum_sha256 IS NOT NULL)),
    CHECK (upload_status <> 'PENDING' OR uploaded_at IS NULL),
    CHECK (source_type <> 'USER_ACTION' OR uploaded_by_user_id IS NOT NULL)
);
CREATE TABLE document_links (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    document_id uuid NOT NULL,
    entity_type varchar NOT NULL CHECK (entity_type IN ('TRIP_STOP')),
    entity_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    FOREIGN KEY (tenant_id,document_id) REFERENCES documents(tenant_id,id),
    UNIQUE (tenant_id,document_id,entity_type,entity_id)
);
CREATE INDEX documents_pending_uploads ON documents(tenant_id,created_at,id) WHERE upload_status='PENDING';
CREATE INDEX document_links_target ON document_links(tenant_id,entity_type,entity_id);
-- Finalized publication identity and metadata cannot be rewritten, even by a runtime SQL bug.
CREATE FUNCTION guard_finalized_document() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.upload_status='UPLOADED' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='Finalized document is immutable';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION guard_finalized_document() FROM PUBLIC;
CREATE TRIGGER finalized_document_immutable BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION guard_finalized_document();
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_links ENABLE ROW LEVEL SECURITY;
CREATE POLICY documents_tenant ON documents TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY document_links_tenant ON document_links TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON documents,document_links TO "${runtimeRole}";
-- Row locking is supported by publication-only column UPDATE privileges.
GRANT UPDATE(object_key,checksum_sha256,upload_status,uploaded_at) ON documents TO "${runtimeRole}";
