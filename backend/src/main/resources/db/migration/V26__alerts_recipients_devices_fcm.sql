-- #56: preserve V24 occurrences; delivery remains a secondary channel.
ALTER TABLE alerts DROP CONSTRAINT alerts_status_check,
 DROP CONSTRAINT alerts_acknowledged_at_check, DROP CONSTRAINT alerts_acknowledged_by_check,
 DROP CONSTRAINT alerts_resolved_at_check, DROP CONSTRAINT alerts_resolved_by_check,
 DROP CONSTRAINT alerts_version_check;
ALTER TABLE alerts ADD COLUMN resolution_reason text NULL;
ALTER TABLE alerts ADD CONSTRAINT alerts_version_check CHECK(version>=0),
 ADD CONSTRAINT alerts_status_check CHECK(status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
 ADD CONSTRAINT alerts_ack_pair CHECK((acknowledged_at IS NULL)=(acknowledged_by IS NULL)),
 ADD CONSTRAINT alerts_resolution_pair CHECK((resolved_at IS NULL)=(resolved_by IS NULL)),
 ADD CONSTRAINT alerts_lifecycle_check CHECK(
  (status='OPEN' AND acknowledged_at IS NULL AND resolved_at IS NULL AND resolution_reason IS NULL)
  OR (status='ACKNOWLEDGED' AND acknowledged_at>=created_at AND acknowledged_by IS NOT NULL AND resolved_at IS NULL AND resolution_reason IS NULL)
  OR (status='RESOLVED' AND resolved_at>=created_at AND resolved_by IS NOT NULL
    AND (acknowledged_at IS NULL OR resolved_at>=acknowledged_at)
    AND resolution_reason IS NOT NULL AND resolution_reason ~ '[^[:space:]]' AND length(resolution_reason)<=4000));
DROP TRIGGER alerts_history ON alerts;
CREATE FUNCTION guard_alert_lifecycle() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Alert history is immutable' USING ERRCODE='42501'; END IF;
 IF TG_OP='INSERT' THEN
  IF NEW.status<>'OPEN' OR NEW.version<>0 THEN RAISE EXCEPTION 'Invalid initial Alert' USING ERRCODE='23514'; END IF;
  RETURN NEW;
 END IF;
 IF OLD.status='RESOLVED' OR NEW.version<>OLD.version+1 OR
  (NEW.id,NEW.tenant_id,NEW.alert_type,NEW.severity,NEW.related_entity_type,NEW.related_entity_id,NEW.message_data,NEW.created_at,NEW.client_operation_id)
  IS DISTINCT FROM (OLD.id,OLD.tenant_id,OLD.alert_type,OLD.severity,OLD.related_entity_type,OLD.related_entity_id,OLD.message_data,OLD.created_at,OLD.client_operation_id)
 THEN RAISE EXCEPTION 'Invalid Alert history mutation' USING ERRCODE='42501'; END IF;
 IF OLD.status='OPEN' AND NEW.status='ACKNOWLEDGED' THEN
  NEW.acknowledged_at=clock_timestamp();
 ELSIF OLD.status IN ('OPEN','ACKNOWLEDGED') AND NEW.status='RESOLVED' THEN
  IF (NEW.acknowledged_at,NEW.acknowledged_by) IS DISTINCT FROM (OLD.acknowledged_at,OLD.acknowledged_by)
  THEN RAISE EXCEPTION 'Acknowledgement is immutable' USING ERRCODE='42501'; END IF;
  -- A resolution never repairs the physical projection. Lock its current fact.
  PERFORM 1 FROM public.trailers t WHERE t.tenant_id=NEW.tenant_id AND t.id=NEW.related_entity_id
   AND t.active AND t.location_state='AT_LOCATION' AND t.current_location_id IS NOT NULL FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Missing Trailer condition persists' USING ERRCODE='23514'; END IF;
  NEW.resolved_at=clock_timestamp();
 ELSE RAISE EXCEPTION 'Invalid Alert transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_alert_lifecycle() FROM PUBLIC;
CREATE TRIGGER alerts_history BEFORE INSERT OR UPDATE OR DELETE ON alerts FOR EACH ROW EXECUTE FUNCTION guard_alert_lifecycle();
CREATE INDEX alerts_created_page ON alerts(tenant_id,created_at DESC,id DESC);
GRANT UPDATE ON alerts TO "${runtimeRole}";

CREATE TABLE alert_recipients (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, alert_id uuid NOT NULL, user_id uuid NOT NULL,
 policy_code varchar(64) NOT NULL CHECK(policy_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
 push_status varchar NOT NULL DEFAULT 'PENDING' CHECK(push_status IN
 ('PENDING','ATTEMPTED','ACCEPTED_BY_FCM','RETRYABLE_FAILURE','PERMANENT_FAILURE','INVALIDATED_TOKEN',
  'SUPPRESSED_AUTHORIZATION','ALERT_INELIGIBLE','NO_ELIGIBLE_DEVICE','PARTIAL_FAILURE')),
 seen_at timestamptz NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(tenant_id,alert_id,user_id), UNIQUE(tenant_id,id), UNIQUE(tenant_id,id,user_id),
 FOREIGN KEY(tenant_id,alert_id) REFERENCES alerts(tenant_id,id),
 FOREIGN KEY(tenant_id,user_id) REFERENCES tenant_memberships(tenant_id,user_id),
 CHECK(seen_at IS NULL OR seen_at>=created_at), CHECK(updated_at>=created_at)
);
CREATE INDEX alert_recipients_user ON alert_recipients(tenant_id,user_id,created_at DESC,id DESC);
CREATE FUNCTION guard_alert_recipient() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Recipient history is retained' USING ERRCODE='42501'; END IF;
 IF TG_OP='UPDATE' AND (NEW.id,NEW.tenant_id,NEW.alert_id,NEW.user_id,NEW.policy_code,NEW.created_at)
 IS DISTINCT FROM (OLD.id,OLD.tenant_id,OLD.alert_id,OLD.user_id,OLD.policy_code,OLD.created_at)
 THEN RAISE EXCEPTION 'Recipient identity is immutable' USING ERRCODE='42501'; END IF;
 NEW.updated_at=clock_timestamp(); RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_alert_recipient() FROM PUBLIC;
CREATE TRIGGER alert_recipient_history BEFORE UPDATE OR DELETE ON alert_recipients FOR EACH ROW EXECUTE FUNCTION guard_alert_recipient();

CREATE TABLE user_devices (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id),
 platform varchar NOT NULL CHECK(platform IN ('ANDROID','IOS')),
 fcm_token varchar(4096) NULL CHECK(fcm_token IS NULL OR (length(fcm_token)>0 AND fcm_token !~ '[[:space:][:cntrl:]]')),
 active boolean NOT NULL, token_version bigint NOT NULL DEFAULT 0 CHECK(token_version>=0),
 last_seen_at timestamptz NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 UNIQUE(id,user_id), CHECK(updated_at>=created_at AND last_seen_at>=created_at),
 CHECK(active OR fcm_token IS NULL)
);
CREATE UNIQUE INDEX user_devices_token ON user_devices(fcm_token) WHERE fcm_token IS NOT NULL;
CREATE INDEX user_devices_owner ON user_devices(user_id,active,id);
CREATE FUNCTION guard_device_history() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Registration history is retained' USING ERRCODE='42501'; END IF;
 IF (NEW.id,NEW.user_id,NEW.platform,NEW.created_at) IS DISTINCT FROM (OLD.id,OLD.user_id,OLD.platform,OLD.created_at)
 THEN RAISE EXCEPTION 'Registration identity is immutable' USING ERRCODE='42501'; END IF;
 NEW.token_version=OLD.token_version+CASE WHEN NEW.fcm_token IS DISTINCT FROM OLD.fcm_token THEN 1 ELSE 0 END;
 NEW.updated_at=clock_timestamp(); RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_device_history() FROM PUBLIC;
CREATE TRIGGER user_device_history BEFORE UPDATE OR DELETE ON user_devices FOR EACH ROW EXECUTE FUNCTION guard_device_history();
ALTER TABLE user_devices ENABLE ROW LEVEL SECURITY;
CREATE POLICY user_devices_owner ON user_devices TO "${runtimeRole}"
 USING(user_id=NULLIF(current_setting('app.current_user_id',true),'')::uuid AND NULLIF(current_setting('app.current_tenant_id',true),'') IS NULL)
 WITH CHECK(user_id=NULLIF(current_setting('app.current_user_id',true),'')::uuid AND NULLIF(current_setting('app.current_tenant_id',true),'') IS NULL);
-- Runtime cannot INSERT/UPDATE directly: transfer requires the single controlled path.
GRANT SELECT ON user_devices TO "${runtimeRole}";

-- Narrow User-authorized registry path; no directory or caller-selected owner.
CREATE FUNCTION register_user_device(registration uuid, device_platform text, token text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE owner_id uuid; result_id uuid; prior_owner uuid; prior_platform text; registered_at timestamptz=clock_timestamp();
BEGIN
 owner_id=NULLIF(current_setting('app.current_user_id',true),'')::uuid;
 IF owner_id IS NULL OR NULLIF(current_setting('app.current_tenant_id',true),'') IS NOT NULL
  OR NOT EXISTS(SELECT 1 FROM public.users WHERE id=owner_id) THEN RAISE EXCEPTION 'User context required' USING ERRCODE='42501'; END IF;
 IF device_platform IS NULL OR device_platform NOT IN ('ANDROID','IOS') OR
  (token IS NOT NULL AND (length(token)<1 OR length(token)>4096 OR token ~ '[[:space:][:cntrl:]]'))
 THEN RAISE EXCEPTION 'Invalid registration' USING ERRCODE='23514'; END IF;
 -- Serialize only short registry writes; never hold this lock during network IO.
 PERFORM pg_advisory_xact_lock(hashtextextended('trailersaas-device-registry',0));
 result_id=registration;
 IF result_id IS NULL AND token IS NOT NULL THEN
  SELECT id INTO result_id FROM public.user_devices WHERE user_id=owner_id AND fcm_token=token AND platform=device_platform;
 END IF;
 IF result_id IS NULL THEN result_id=gen_random_uuid(); END IF;
 SELECT user_id,platform INTO prior_owner,prior_platform FROM public.user_devices WHERE id=result_id;
 IF FOUND AND (prior_owner<>owner_id OR prior_platform<>device_platform)
 THEN RAISE EXCEPTION 'Registration unavailable' USING ERRCODE='42501'; END IF;
 IF prior_owner IS NULL AND (SELECT count(*) FROM public.user_devices WHERE user_id=owner_id AND active)>=32
 THEN RAISE EXCEPTION 'Active registration bound exceeded' USING ERRCODE='23514'; END IF;
 IF token IS NOT NULL THEN
  UPDATE public.user_devices SET active=false,fcm_token=NULL WHERE fcm_token=token AND id<>result_id;
 END IF;
 INSERT INTO public.user_devices(id,user_id,platform,fcm_token,active,last_seen_at,created_at,updated_at)
 VALUES(result_id,owner_id,device_platform,token,true,registered_at,registered_at,registered_at)
 ON CONFLICT(id) DO UPDATE SET fcm_token=EXCLUDED.fcm_token,active=true,last_seen_at=clock_timestamp();
 RETURN result_id;
END $$;
REVOKE ALL ON FUNCTION register_user_device(uuid,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION register_user_device(uuid,text,text) TO "${runtimeRole}";
CREATE FUNCTION deactivate_user_device(registration uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
 IF NULLIF(current_setting('app.current_user_id',true),'') IS NULL OR NULLIF(current_setting('app.current_tenant_id',true),'') IS NOT NULL
 THEN RAISE EXCEPTION 'User context required' USING ERRCODE='42501'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended('trailersaas-device-registry',0));
 UPDATE public.user_devices SET active=false,fcm_token=NULL WHERE id=registration AND user_id=NULLIF(current_setting('app.current_user_id',true),'')::uuid;
 RETURN FOUND;
END $$;
REVOKE ALL ON FUNCTION deactivate_user_device(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION deactivate_user_device(uuid) TO "${runtimeRole}";

-- Optional historical provenance, never a new requirement or authorization key.
-- device_id already exists, reserved by V16; only add its now-available FK.
ALTER TABLE processed_client_operations ADD CONSTRAINT mobile_operation_device_owner
 FOREIGN KEY(device_id,user_id) REFERENCES user_devices(id,user_id);

CREATE TABLE alert_deliveries (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, recipient_id uuid NOT NULL, user_id uuid NOT NULL,
 device_id uuid NOT NULL, token_version bigint NOT NULL CHECK(token_version>=0),
 status varchar NOT NULL CHECK(status IN ('PENDING','ATTEMPTED','ACCEPTED_BY_FCM','RETRYABLE_FAILURE','PERMANENT_FAILURE','INVALIDATED_TOKEN','SUPPRESSED_AUTHORIZATION')),
 attempt_count integer NOT NULL DEFAULT 0 CHECK(attempt_count>=0),
 last_code varchar NULL CHECK(last_code IN ('FCM_ACCEPTED','FCM_TRANSIENT','FCM_PERMANENT','TOKEN_INVALID','AUTHORIZATION_CHANGED','TRANSPORT_DISABLED')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(), updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(tenant_id,recipient_id,device_id),
 FOREIGN KEY(tenant_id,recipient_id,user_id) REFERENCES alert_recipients(tenant_id,id,user_id),
 FOREIGN KEY(device_id,user_id) REFERENCES user_devices(id,user_id), CHECK(updated_at>=created_at)
);
CREATE INDEX alert_deliveries_diagnostics ON alert_deliveries(tenant_id,recipient_id,status);
ALTER TABLE alert_recipients ENABLE ROW LEVEL SECURITY;
ALTER TABLE alert_deliveries ENABLE ROW LEVEL SECURITY;
CREATE POLICY alert_recipients_tenant ON alert_recipients TO "${runtimeRole}"
 USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
 WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY alert_deliveries_tenant ON alert_deliveries TO "${runtimeRole}"
 USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
 WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT,UPDATE ON alert_recipients,alert_deliveries TO "${runtimeRole}";

-- Controlled cross-User token lookup ONLY for an existing live Tenant delivery job.
-- Mirrors the conservative ALERT_VIEW Tenant grants; no Customer/Driver/Yard subscription.
CREATE FUNCTION alert_delivery_devices(job uuid, recipient uuid)
RETURNS TABLE(device_id uuid,user_id uuid,fcm_token varchar,token_version bigint)
LANGUAGE sql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
 SELECT d.id,d.user_id,d.fcm_token,d.token_version FROM public.user_devices d
 JOIN public.alert_recipients r ON r.user_id=d.user_id
 JOIN public.alerts a ON a.tenant_id=r.tenant_id AND a.id=r.alert_id
 JOIN public.outbox_jobs j ON j.tenant_id=r.tenant_id AND j.id=job
 WHERE r.id=recipient AND r.tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid
 AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL
 AND j.job_type='ALERT_FCM' AND j.aggregate_type='ALERT' AND j.aggregate_id=a.id
 AND j.payload->>'schemaVersion'='1' AND j.payload->'data'->>'recipientId'=r.id::text
 AND j.status='PROCESSING' AND j.locked_until>clock_timestamp()
 AND a.status IN ('OPEN','ACKNOWLEDGED') AND d.active AND d.fcm_token IS NOT NULL
 AND EXISTS(SELECT 1 FROM public.tenant_memberships m JOIN public.role_assignments ra
   ON ra.tenant_id=m.tenant_id AND ra.membership_id=m.id
   WHERE m.tenant_id=r.tenant_id AND m.user_id=r.user_id AND m.status='ACTIVE'
   AND ra.scope_type='TENANT' AND ra.role_code IN ('TENANT_ADMIN','TRANSPORT_OPERATIONS'))
 AND EXISTS(SELECT 1 FROM public.trailers t WHERE t.tenant_id=a.tenant_id AND t.id=a.related_entity_id)
 ORDER BY d.id LIMIT 32
$$;
REVOKE ALL ON FUNCTION alert_delivery_devices(uuid,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION alert_delivery_devices(uuid,uuid) TO "${runtimeRole}";
CREATE FUNCTION invalidate_alert_device(job uuid,recipient uuid,device uuid,revision bigint) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended('trailersaas-device-registry',0));
 UPDATE public.user_devices d SET active=false,fcm_token=NULL
 WHERE d.id=device AND d.token_version=revision AND EXISTS(
  SELECT 1 FROM public.alert_delivery_devices(job,recipient) allowed WHERE allowed.device_id=d.id AND allowed.user_id=d.user_id AND allowed.token_version=revision);
 RETURN FOUND;
END $$;
REVOKE ALL ON FUNCTION invalidate_alert_device(uuid,uuid,uuid,bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION invalidate_alert_device(uuid,uuid,uuid,bigint) TO "${runtimeRole}";
