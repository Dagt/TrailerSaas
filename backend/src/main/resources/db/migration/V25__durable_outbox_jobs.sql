-- ADR-022 / amendment Q. Runtime is neither owner nor BYPASSRLS.
CREATE TABLE outbox_jobs (
    id uuid PRIMARY KEY,
    tenant_id uuid REFERENCES tenants(id),
    job_type varchar NOT NULL CHECK (job_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    aggregate_type varchar NULL CHECK (aggregate_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    aggregate_id uuid NULL,
    payload jsonb NOT NULL CHECK (
        jsonb_typeof(payload) = 'object'
        AND payload ?& ARRAY['schemaVersion', 'data']
        AND payload - 'schemaVersion' - 'data' = '{}'::jsonb
        AND jsonb_typeof(payload->'schemaVersion') = 'number'
        AND payload->>'schemaVersion' ~ '^[1-9][0-9]{0,2}$'
        AND jsonb_typeof(payload->'data') = 'object'
        AND octet_length(payload::text) <= 16384
    ),
    status varchar NOT NULL CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','DEAD')),
    available_at timestamptz NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts integer NOT NULL CHECK (max_attempts BETWEEN 1 AND 20),
    locked_by uuid NULL,
    locked_until timestamptz NULL,
    last_error text NULL CHECK (last_error IN (
        'UNKNOWN_JOB_TYPE','UNSUPPORTED_PAYLOAD_VERSION','INVALID_PAYLOAD','INVALID_SCOPE',
        'HANDLER_RETRYABLE','HANDLER_PERMANENT','HANDLER_TIMEOUT','HANDLER_FAILURE',
        'LEASE_EXPIRED','LEASE_EXHAUSTED')),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    processed_at timestamptz NULL,
    CHECK ((aggregate_type IS NULL) = (aggregate_id IS NULL)),
    CHECK (attempt_count <= max_attempts),
    CHECK (
        (status='PENDING' AND locked_by IS NULL AND locked_until IS NULL
            AND processed_at IS NULL AND attempt_count < max_attempts)
        OR (status='PROCESSING' AND locked_by IS NOT NULL AND locked_until IS NOT NULL
            AND processed_at IS NULL AND attempt_count > 0)
        OR (status IN ('SUCCEEDED','DEAD') AND processed_at IS NOT NULL
            AND locked_by IS NULL AND locked_until IS NULL AND attempt_count > 0)
    )
);

CREATE INDEX outbox_pending_tenant ON outbox_jobs(tenant_id,available_at,id)
    WHERE status='PENDING' AND tenant_id IS NOT NULL;
CREATE INDEX outbox_processing_tenant ON outbox_jobs(tenant_id,locked_until,id)
    WHERE status='PROCESSING' AND tenant_id IS NOT NULL;
CREATE INDEX outbox_monitoring ON outbox_jobs(tenant_id,status,created_at);
CREATE INDEX outbox_pending_global ON outbox_jobs(available_at,id)
    WHERE status='PENDING' AND tenant_id IS NULL;
CREATE INDEX outbox_processing_global ON outbox_jobs(locked_until,id)
    WHERE status='PROCESSING' AND tenant_id IS NULL;

CREATE FUNCTION guard_outbox_job() RETURNS trigger LANGUAGE plpgsql
    SET search_path=pg_catalog,public AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Job history cannot be deleted by runtime' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.status<>'PENDING' OR NEW.attempt_count<>0 OR NEW.last_error IS NOT NULL THEN
            RAISE EXCEPTION 'Job must start pending without attempts' USING ERRCODE='23514';
        END IF;
        NEW.created_at:=clock_timestamp(); NEW.updated_at:=NEW.created_at;
        RETURN NEW;
    END IF;
    IF OLD.status IN ('SUCCEEDED','DEAD') OR
       ROW(NEW.id,NEW.tenant_id,NEW.job_type,NEW.aggregate_type,NEW.aggregate_id,
           NEW.payload,NEW.max_attempts,NEW.created_at) IS DISTINCT FROM
       ROW(OLD.id,OLD.tenant_id,OLD.job_type,OLD.aggregate_type,OLD.aggregate_id,
           OLD.payload,OLD.max_attempts,OLD.created_at) THEN
        RAISE EXCEPTION 'Job intent and terminal history are immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.status='PROCESSING' THEN
        IF NEW.attempt_count<>OLD.attempt_count+1 OR OLD.attempt_count>=OLD.max_attempts
           OR NEW.locked_until<=clock_timestamp()
           OR (OLD.status='PENDING' AND OLD.available_at>clock_timestamp())
           OR (OLD.status='PROCESSING' AND (OLD.locked_until>clock_timestamp()
               OR NEW.locked_by=OLD.locked_by))
           OR OLD.status NOT IN ('PENDING','PROCESSING') THEN
            RAISE EXCEPTION 'Invalid claim or lease recovery' USING ERRCODE='23514';
        END IF;
    ELSE
        IF OLD.status<>'PROCESSING' OR NEW.attempt_count<>OLD.attempt_count
           OR (NEW.status='PENDING' AND (OLD.attempt_count>=OLD.max_attempts
               OR NEW.available_at<=clock_timestamp()))
           OR (OLD.locked_until<=clock_timestamp() AND
               (NEW.status<>'DEAD' OR OLD.attempt_count<OLD.max_attempts
                OR NEW.last_error IS DISTINCT FROM 'LEASE_EXHAUSTED')) THEN
            RAISE EXCEPTION 'Invalid result, retry or exhaustion transition' USING ERRCODE='23514';
        END IF;
    END IF;
    NEW.updated_at:=clock_timestamp();
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_outbox_job() FROM PUBLIC;
CREATE TRIGGER outbox_job_history BEFORE INSERT OR UPDATE OR DELETE ON outbox_jobs
    FOR EACH ROW EXECUTE FUNCTION guard_outbox_job();

ALTER TABLE outbox_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_tenant ON outbox_jobs TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
        AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT,UPDATE ON outbox_jobs TO "${runtimeRole}";
