-- #57: system observations never fabricate a User, mobile operation or Yard finding.
ALTER TABLE outbox_jobs ADD CONSTRAINT uq_job_tenant_reference UNIQUE(tenant_id,id);
CREATE TABLE stale_trailer_scans (
 tenant_id uuid PRIMARY KEY REFERENCES tenants(id),
 scan_id uuid NULL, job_id uuid NULL, in_progress boolean NOT NULL DEFAULT false,
 cursor_id uuid NULL, upper_bound uuid NULL,
 next_scan_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 started_at timestamptz NULL, completed_at timestamptz NULL,
 FOREIGN KEY(tenant_id,job_id) REFERENCES outbox_jobs(tenant_id,id),
 CHECK(NOT in_progress OR (scan_id IS NOT NULL AND started_at IS NOT NULL)),
 CHECK(cursor_id IS NULL OR (upper_bound IS NOT NULL AND cursor_id<=upper_bound)),
 CHECK(completed_at IS NULL OR completed_at>=started_at)
);
ALTER TABLE stale_trailer_scans ENABLE ROW LEVEL SECURITY;
CREATE POLICY stale_trailer_scans_tenant ON stale_trailer_scans TO "${runtimeRole}"
 USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
 WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT,UPDATE ON stale_trailer_scans TO "${runtimeRole}";

ALTER TABLE alerts DROP CONSTRAINT alerts_alert_type_check,
 DROP CONSTRAINT alerts_lifecycle_check, DROP CONSTRAINT alerts_resolution_pair,
 ALTER COLUMN client_operation_id DROP NOT NULL;
ALTER TABLE alerts
 ADD COLUMN origin_actor_type varchar NOT NULL DEFAULT 'HUMAN_USER',
 ADD COLUMN detector_job_id uuid NULL,
 ADD COLUMN detected_last_seen_at timestamptz NULL,
 ADD COLUMN detected_threshold_hours integer NULL,
 ADD COLUMN resolution_actor_type varchar NULL,
 ADD COLUMN resolution_job_id uuid NULL,
 ADD COLUMN resolution_last_seen_at timestamptz NULL,
 ADD COLUMN resolution_threshold_hours integer NULL,
 ADD CONSTRAINT alerts_detector_job FOREIGN KEY(tenant_id,detector_job_id) REFERENCES outbox_jobs(tenant_id,id),
 ADD CONSTRAINT alerts_resolution_job FOREIGN KEY(tenant_id,resolution_job_id) REFERENCES outbox_jobs(tenant_id,id);
-- Existing history records the real existing human resolver; no invented actor.
DROP TRIGGER alerts_history ON alerts;
UPDATE alerts SET resolution_actor_type='HUMAN_USER' WHERE status='RESOLVED';

ALTER TABLE alerts ADD CONSTRAINT alerts_alert_type_check CHECK(alert_type IN ('TRAILER_MISSING','STALE_TRAILER')),
 ADD CONSTRAINT alerts_origin_check CHECK(
  (alert_type='TRAILER_MISSING' AND origin_actor_type='HUMAN_USER' AND client_operation_id IS NOT NULL
   AND detector_job_id IS NULL AND detected_last_seen_at IS NULL AND detected_threshold_hours IS NULL)
  OR (alert_type='STALE_TRAILER' AND origin_actor_type='SYSTEM' AND client_operation_id IS NULL
   AND detector_job_id IS NOT NULL AND detected_last_seen_at IS NOT NULL AND detected_threshold_hours IS NOT NULL AND detected_threshold_hours>0
   AND message_data='{}'::jsonb AND isfinite(created_at) AND isfinite(detected_last_seen_at)
   AND extract(epoch from created_at)-extract(epoch from detected_last_seen_at)>=detected_threshold_hours::numeric*3600)),
 ADD CONSTRAINT alerts_resolution_pair CHECK(
  (resolved_at IS NULL AND resolved_by IS NULL AND resolution_actor_type IS NULL AND resolution_job_id IS NULL
   AND resolution_last_seen_at IS NULL AND resolution_threshold_hours IS NULL)
  OR (resolved_at IS NOT NULL AND resolution_actor_type IS NOT NULL AND resolution_actor_type='HUMAN_USER' AND resolved_by IS NOT NULL AND resolution_job_id IS NULL
   AND resolution_last_seen_at IS NULL AND resolution_threshold_hours IS NULL)
  OR (resolved_at IS NOT NULL AND alert_type='STALE_TRAILER' AND resolution_actor_type IS NOT NULL AND resolution_actor_type='SYSTEM' AND resolved_by IS NULL
   AND resolution_job_id IS NOT NULL AND resolution_last_seen_at IS NOT NULL AND resolution_threshold_hours IS NOT NULL AND resolution_threshold_hours>0)),
 ADD CONSTRAINT alerts_lifecycle_check CHECK(
  (status='OPEN' AND acknowledged_at IS NULL AND resolved_at IS NULL AND resolution_reason IS NULL)
  OR (status='ACKNOWLEDGED' AND acknowledged_at>=created_at AND acknowledged_by IS NOT NULL AND resolved_at IS NULL AND resolution_reason IS NULL)
  OR (status='RESOLVED' AND resolved_at>=created_at AND (acknowledged_at IS NULL OR resolved_at>=acknowledged_at)
      AND resolution_reason IS NOT NULL AND resolution_reason ~ '[^[:space:]]' AND length(resolution_reason)<=4000));
CREATE UNIQUE INDEX alerts_unresolved_stale ON alerts(tenant_id,related_entity_id)
 WHERE alert_type='STALE_TRAILER' AND status IN ('OPEN','ACKNOWLEDGED');
CREATE INDEX trailers_stale_scan ON trailers(tenant_id,id) INCLUDE(active,last_seen_at);

-- Preserve V24 function bodies and all Missing invariants. Only Missing invokes them.
DROP TRIGGER yard_missing_alert ON alerts;
CREATE TRIGGER yard_missing_alert BEFORE INSERT ON alerts FOR EACH ROW
 WHEN(NEW.alert_type='TRAILER_MISSING') EXECUTE FUNCTION guard_yard_missing_alert();
DROP TRIGGER yard_alert_association ON alerts;
CREATE CONSTRAINT TRIGGER yard_alert_association AFTER INSERT ON alerts DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW WHEN(NEW.alert_type='TRAILER_MISSING') EXECUTE FUNCTION guard_yard_alert_association();

CREATE FUNCTION stale_job_is_current(tenant uuid,job uuid) RETURNS boolean LANGUAGE sql
 SET search_path=pg_catalog,public AS $$
 SELECT EXISTS(SELECT 1 FROM public.outbox_jobs j JOIN public.stale_trailer_scans s
  ON s.tenant_id=j.tenant_id AND s.job_id=j.id JOIN public.tenants t ON t.id=j.tenant_id
  WHERE j.tenant_id=tenant AND j.id=job AND t.status='ACTIVE'
   AND tenant=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
   AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL
   AND s.in_progress AND j.job_type='STALE_SCAN' AND j.aggregate_type='TENANT' AND j.aggregate_id=tenant
   AND j.payload->>'schemaVersion'='1' AND j.payload->'data'->>'scanId'=s.scan_id::text
   AND j.status='PROCESSING' AND j.locked_until>clock_timestamp())
$$;
REVOKE ALL ON FUNCTION stale_job_is_current(uuid,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION stale_job_is_current(uuid,uuid) TO "${runtimeRole}";

-- SELECT FOR SHARE requires UPDATE privilege, which runtime must NOT gain on
-- Tenant administration. This closed function only locks the current live job's
-- own Tenant row; no directory, mutation or caller-selected foreign Tenant.
CREATE FUNCTION lock_stale_tenant(tenant uuid,job uuid) RETURNS boolean LANGUAGE plpgsql
 SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE state varchar;
BEGIN
 IF NOT public.stale_job_is_current(tenant,job) THEN RETURN false; END IF;
 SELECT status INTO state FROM public.tenants WHERE id=tenant FOR SHARE;
 RETURN state='ACTIVE';
END $$;
REVOKE ALL ON FUNCTION lock_stale_tenant(uuid,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lock_stale_tenant(uuid,uuid) TO "${runtimeRole}";

CREATE OR REPLACE FUNCTION guard_alert_lifecycle() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE seen timestamptz; hours integer; evaluated timestamptz;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Alert history is immutable' USING ERRCODE='42501'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'OPEN' OR NEW.version<>0 THEN RAISE EXCEPTION 'Invalid initial Alert' USING ERRCODE='23514'; END IF;
  IF NEW.alert_type='STALE_TRAILER' THEN
   IF NOT public.lock_stale_tenant(NEW.tenant_id,NEW.detector_job_id) THEN RAISE EXCEPTION 'Live scoped detector required' USING ERRCODE='23514'; END IF;
   SELECT stale_trailer_threshold_hours INTO hours FROM public.tenant_settings WHERE tenant_id=NEW.tenant_id FOR SHARE;
   SELECT last_seen_at INTO seen FROM public.trailers WHERE tenant_id=NEW.tenant_id AND id=NEW.related_entity_id AND active FOR UPDATE;
   IF NOT FOUND OR seen IS NULL OR NOT isfinite(seen) OR hours IS NULL OR NEW.detected_last_seen_at IS DISTINCT FROM seen
    OR NEW.detected_threshold_hours IS DISTINCT FROM hours OR NEW.created_at>clock_timestamp()
    OR NEW.created_at<(SELECT created_at FROM public.outbox_jobs WHERE id=NEW.detector_job_id)
    OR extract(epoch from NEW.created_at)-extract(epoch from seen)<hours::numeric*3600
   THEN RAISE EXCEPTION 'Invalid stale physical evidence' USING ERRCODE='23514'; END IF;
  END IF;
  RETURN NEW;
 END IF;
 IF OLD.status='RESOLVED' OR NEW.version<>OLD.version+1 OR
  (NEW.id,NEW.tenant_id,NEW.alert_type,NEW.severity,NEW.related_entity_type,NEW.related_entity_id,NEW.message_data,NEW.created_at,NEW.client_operation_id,
   NEW.origin_actor_type,NEW.detector_job_id,NEW.detected_last_seen_at,NEW.detected_threshold_hours)
  IS DISTINCT FROM (OLD.id,OLD.tenant_id,OLD.alert_type,OLD.severity,OLD.related_entity_type,OLD.related_entity_id,OLD.message_data,OLD.created_at,OLD.client_operation_id,
   OLD.origin_actor_type,OLD.detector_job_id,OLD.detected_last_seen_at,OLD.detected_threshold_hours)
 THEN RAISE EXCEPTION 'Invalid Alert history mutation' USING ERRCODE='42501'; END IF;
 IF OLD.status='OPEN' AND NEW.status='ACKNOWLEDGED' THEN
  NEW.acknowledged_at=clock_timestamp();
 ELSIF OLD.status IN ('OPEN','ACKNOWLEDGED') AND NEW.status='RESOLVED' THEN
  IF (NEW.acknowledged_at,NEW.acknowledged_by) IS DISTINCT FROM (OLD.acknowledged_at,OLD.acknowledged_by)
  THEN RAISE EXCEPTION 'Acknowledgement is immutable' USING ERRCODE='42501'; END IF;
  IF NEW.alert_type='TRAILER_MISSING' THEN
   PERFORM 1 FROM public.trailers t WHERE t.tenant_id=NEW.tenant_id AND t.id=NEW.related_entity_id
    AND t.active AND t.location_state='AT_LOCATION' AND t.current_location_id IS NOT NULL FOR SHARE;
   IF NOT FOUND OR NEW.resolved_by IS NULL THEN RAISE EXCEPTION 'Missing Trailer condition persists' USING ERRCODE='23514'; END IF;
   NEW.resolution_actor_type='HUMAN_USER';
  ELSE
   SELECT stale_trailer_threshold_hours INTO hours FROM public.tenant_settings WHERE tenant_id=NEW.tenant_id FOR SHARE;
   SELECT last_seen_at INTO seen FROM public.trailers WHERE tenant_id=NEW.tenant_id AND id=NEW.related_entity_id AND active FOR SHARE;
   evaluated=clock_timestamp();
   IF NOT FOUND OR seen IS NULL OR NOT isfinite(seen) OR hours IS NULL OR seen>evaluated OR extract(epoch from evaluated)-extract(epoch from seen)>=hours::numeric*3600
   THEN RAISE EXCEPTION 'Stale Trailer condition persists' USING ERRCODE='23514'; END IF;
   IF NEW.resolved_by IS NULL THEN
    IF NOT public.lock_stale_tenant(NEW.tenant_id,NEW.resolution_job_id)
    THEN RAISE EXCEPTION 'Live scoped detector required' USING ERRCODE='23514'; END IF;
    NEW.resolution_actor_type='SYSTEM';NEW.resolution_last_seen_at=seen;NEW.resolution_threshold_hours=hours;
   ELSE NEW.resolution_actor_type='HUMAN_USER'; END IF;
  END IF;
  NEW.resolved_at=clock_timestamp();
 ELSE RAISE EXCEPTION 'Invalid Alert transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER alerts_history BEFORE INSERT OR UPDATE OR DELETE ON alerts FOR EACH ROW EXECUTE FUNCTION guard_alert_lifecycle();
