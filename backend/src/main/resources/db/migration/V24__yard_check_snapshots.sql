-- #50: immutable expected inventory, one-way observations, terminal history.
CREATE TABLE yard_checks (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  site_id uuid NOT NULL,
  location_id uuid NULL,
  status varchar NOT NULL CHECK(status IN ('IN_PROGRESS','COMPLETED','CANCELLED')),
  started_by_user_id uuid NOT NULL REFERENCES users(id),
  started_at timestamptz NOT NULL,
  completed_at timestamptz NULL,
  completed_by_user_id uuid NULL REFERENCES users(id),
  cancelled_at timestamptz NULL,
  cancelled_by_user_id uuid NULL REFERENCES users(id),
  cancellation_reason text NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
  client_operation_id uuid NOT NULL,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,id,site_id), UNIQUE(tenant_id,client_operation_id),
  FOREIGN KEY(tenant_id,site_id) REFERENCES physical_sites(tenant_id,id),
  FOREIGN KEY(tenant_id,site_id,location_id) REFERENCES operational_locations(tenant_id,site_id,id),
  FOREIGN KEY(tenant_id,client_operation_id) REFERENCES processed_client_operations(tenant_id,client_operation_id),
  CHECK(created_at=started_at AND updated_at>=started_at),
  CHECK((status='IN_PROGRESS' AND completed_at IS NULL AND completed_by_user_id IS NULL AND cancelled_at IS NULL AND cancelled_by_user_id IS NULL AND cancellation_reason IS NULL)
    OR (status='COMPLETED' AND completed_at IS NOT NULL AND completed_at>=started_at AND completed_by_user_id IS NOT NULL AND cancelled_at IS NULL AND cancelled_by_user_id IS NULL AND cancellation_reason IS NULL)
    OR (status='CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_at>=started_at AND cancelled_by_user_id IS NOT NULL AND completed_at IS NULL AND completed_by_user_id IS NULL AND cancellation_reason IS NOT NULL AND cancellation_reason ~ '[^[:space:]]' AND length(cancellation_reason)<=4000))
);
CREATE INDEX yard_checks_list ON yard_checks(tenant_id,site_id,status,id);

CREATE TABLE yard_check_items (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  yard_check_id uuid NOT NULL,
  site_id uuid NOT NULL,
  trailer_id uuid NOT NULL,
  expected boolean NOT NULL,
  expected_location_id uuid NULL,
  recorded_snapshot jsonb NOT NULL CHECK(jsonb_typeof(recorded_snapshot)='object'),
  found boolean NOT NULL DEFAULT false,
  result varchar NULL CHECK(result IN ('MATCHED','MISSING','UNEXPECTED','LOCATION_MISMATCH')),
  observed_location_id uuid NULL,
  scanned_at timestamptz NULL,
  scanned_by_user_id uuid NULL REFERENCES users(id),
  recorded_at timestamptz NULL,
  client_operation_id uuid NULL,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,yard_check_id,id), UNIQUE(tenant_id,yard_check_id,trailer_id),
  FOREIGN KEY(tenant_id,yard_check_id,site_id) REFERENCES yard_checks(tenant_id,id,site_id),
  FOREIGN KEY(tenant_id,trailer_id) REFERENCES trailers(tenant_id,id),
  FOREIGN KEY(tenant_id,site_id,expected_location_id) REFERENCES operational_locations(tenant_id,site_id,id),
  FOREIGN KEY(tenant_id,site_id,observed_location_id) REFERENCES operational_locations(tenant_id,site_id,id),
  FOREIGN KEY(tenant_id,client_operation_id) REFERENCES processed_client_operations(tenant_id,client_operation_id),
  CHECK((expected AND expected_location_id IS NOT NULL) OR (NOT expected AND expected_location_id IS NULL)),
  CHECK((expected AND NOT found AND (result IS NULL OR result='MISSING') AND observed_location_id IS NULL AND scanned_at IS NULL AND scanned_by_user_id IS NULL AND recorded_at IS NULL AND client_operation_id IS NULL)
    OR (found AND result IS NOT NULL AND observed_location_id IS NOT NULL AND scanned_at IS NOT NULL AND scanned_by_user_id IS NOT NULL AND recorded_at IS NOT NULL AND recorded_at>=scanned_at AND client_operation_id IS NOT NULL
      AND ((expected AND result='MATCHED' AND observed_location_id=expected_location_id)
        OR (expected AND result='LOCATION_MISMATCH' AND observed_location_id<>expected_location_id)
        OR (NOT expected AND result='UNEXPECTED'))))
);
CREATE INDEX yard_check_items_page ON yard_check_items(tenant_id,yard_check_id,id);

-- Minimal synchronous persistence authorized for #50; no recipient or worker.
CREATE TABLE alerts (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  alert_type varchar NOT NULL CHECK(alert_type='TRAILER_MISSING'),
  severity varchar NOT NULL CHECK(severity='WARNING'),
  status varchar NOT NULL CHECK(status='OPEN'),
  related_entity_type varchar NOT NULL CHECK(related_entity_type='TRAILER'),
  related_entity_id uuid NOT NULL,
  message_data jsonb NOT NULL CHECK(jsonb_typeof(message_data)='object'),
  created_at timestamptz NOT NULL,
  acknowledged_at timestamptz NULL CHECK(acknowledged_at IS NULL),
  acknowledged_by uuid NULL REFERENCES users(id) CHECK(acknowledged_by IS NULL),
  resolved_at timestamptz NULL CHECK(resolved_at IS NULL),
  resolved_by uuid NULL REFERENCES users(id) CHECK(resolved_by IS NULL),
  version bigint NOT NULL DEFAULT 0 CHECK(version=0),
  client_operation_id uuid NOT NULL,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,client_operation_id),
  FOREIGN KEY(tenant_id,related_entity_id) REFERENCES trailers(tenant_id,id),
  FOREIGN KEY(tenant_id,client_operation_id) REFERENCES processed_client_operations(tenant_id,client_operation_id)
);
CREATE INDEX alerts_inbox ON alerts(tenant_id,status,created_at,id);

CREATE TABLE yard_check_reconciliations (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  yard_check_id uuid NOT NULL,
  item_id uuid NOT NULL,
  trailer_id uuid NOT NULL,
  action varchar NOT NULL CHECK(action IN ('MARK_LOCATION_UNKNOWN','LOCATION_CORRECTION')),
  movement_event_id uuid NOT NULL,
  alert_id uuid NULL,
  actor_user_id uuid NOT NULL REFERENCES users(id),
  reason text NOT NULL CHECK(reason ~ '[^[:space:]]' AND length(reason)<=4000),
  occurred_at timestamptz NOT NULL,
  recorded_at timestamptz NOT NULL CHECK(recorded_at>=occurred_at),
  client_operation_id uuid NOT NULL,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,item_id), UNIQUE(tenant_id,movement_event_id), UNIQUE(tenant_id,client_operation_id),
  FOREIGN KEY(tenant_id,yard_check_id,item_id) REFERENCES yard_check_items(tenant_id,yard_check_id,id),
  FOREIGN KEY(tenant_id,trailer_id) REFERENCES trailers(tenant_id,id),
  FOREIGN KEY(tenant_id,movement_event_id) REFERENCES trailer_movement_events(tenant_id,id),
  FOREIGN KEY(tenant_id,alert_id) REFERENCES alerts(tenant_id,id),
  FOREIGN KEY(tenant_id,client_operation_id) REFERENCES processed_client_operations(tenant_id,client_operation_id),
  CHECK((action='MARK_LOCATION_UNKNOWN' AND alert_id IS NOT NULL) OR (action='LOCATION_CORRECTION' AND alert_id IS NULL))
);

CREATE FUNCTION guard_yard_check_lifecycle() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Check history cannot be deleted' USING ERRCODE='42501'; END IF;
  IF OLD.status<>'IN_PROGRESS' OR
    (NEW.id,NEW.tenant_id,NEW.site_id,NEW.location_id,NEW.started_by_user_id,NEW.started_at,NEW.created_at,NEW.client_operation_id)
    IS DISTINCT FROM (OLD.id,OLD.tenant_id,OLD.site_id,OLD.location_id,OLD.started_by_user_id,OLD.started_at,OLD.created_at,OLD.client_operation_id)
    OR NEW.version<>OLD.version+1 THEN
    RAISE EXCEPTION 'Invalid Check lifecycle mutation' USING ERRCODE='42501'; END IF;
  IF NEW.status='COMPLETED' AND EXISTS(SELECT 1 FROM public.yard_check_items WHERE tenant_id=NEW.tenant_id AND yard_check_id=NEW.id AND result IS NULL) THEN
    RAISE EXCEPTION 'Completion has pending items' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_yard_check_lifecycle() FROM PUBLIC;
CREATE TRIGGER yard_check_lifecycle BEFORE UPDATE OR DELETE ON yard_checks FOR EACH ROW EXECUTE FUNCTION guard_yard_check_lifecycle();

CREATE FUNCTION guard_yard_check_item() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
DECLARE parent_status varchar; parent_started timestamptz; parent_xid xid; parent_version bigint;
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Snapshot cannot be deleted' USING ERRCODE='42501'; END IF;
  SELECT status,started_at,xmin,version INTO parent_status,parent_started,parent_xid,parent_version FROM public.yard_checks
    WHERE tenant_id=NEW.tenant_id AND id=NEW.yard_check_id FOR UPDATE;
  IF parent_status IS DISTINCT FROM 'IN_PROGRESS' THEN RAISE EXCEPTION 'Check is terminal' USING ERRCODE='42501'; END IF;
  IF TG_OP='INSERT' THEN
    IF NEW.expected AND (parent_version<>0 OR parent_xid<>pg_current_xact_id()::xid OR NEW.found OR NEW.result IS NOT NULL) THEN
      RAISE EXCEPTION 'Expected inventory is frozen at start' USING ERRCODE='42501'; END IF;
  ELSE
    IF OLD.found OR OLD.result IS NOT NULL OR
      (NEW.id,NEW.tenant_id,NEW.yard_check_id,NEW.site_id,NEW.trailer_id,NEW.expected,NEW.expected_location_id,NEW.recorded_snapshot)
      IS DISTINCT FROM (OLD.id,OLD.tenant_id,OLD.yard_check_id,OLD.site_id,OLD.trailer_id,OLD.expected,OLD.expected_location_id,OLD.recorded_snapshot) THEN
      RAISE EXCEPTION 'Snapshot or observation is immutable' USING ERRCODE='42501'; END IF;
  END IF;
  IF NEW.found AND NEW.scanned_at<parent_started THEN RAISE EXCEPTION 'Scan predates Check' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_yard_check_item() FROM PUBLIC;
CREATE TRIGGER yard_check_item_history BEFORE INSERT OR UPDATE OR DELETE ON yard_check_items FOR EACH ROW EXECUTE FUNCTION guard_yard_check_item();

CREATE FUNCTION guard_yard_missing_completion() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
  IF NEW.result='MISSING' AND NOT EXISTS(SELECT 1 FROM public.yard_checks WHERE tenant_id=NEW.tenant_id AND id=NEW.yard_check_id AND status='COMPLETED') THEN
    RAISE EXCEPTION 'Missing is a completed Check finding' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_yard_missing_completion() FROM PUBLIC;
CREATE CONSTRAINT TRIGGER yard_missing_completion AFTER INSERT OR UPDATE ON yard_check_items
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION guard_yard_missing_completion();

CREATE TRIGGER yard_reconciliation_immutable BEFORE UPDATE OR DELETE ON yard_check_reconciliations FOR EACH ROW EXECUTE FUNCTION guard_yard_history();
CREATE TRIGGER alerts_history BEFORE UPDATE OR DELETE ON alerts FOR EACH ROW EXECUTE FUNCTION guard_yard_history();

ALTER TABLE yard_checks ENABLE ROW LEVEL SECURITY;
ALTER TABLE yard_check_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE yard_check_reconciliations ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
CREATE POLICY yard_checks_tenant ON yard_checks TO "${runtimeRole}" USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL) WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY yard_check_items_tenant ON yard_check_items TO "${runtimeRole}" USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL) WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY yard_check_reconciliations_tenant ON yard_check_reconciliations TO "${runtimeRole}" USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL) WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY alerts_tenant ON alerts TO "${runtimeRole}" USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL) WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT,UPDATE ON yard_checks,yard_check_items TO "${runtimeRole}";
GRANT SELECT,INSERT ON yard_check_reconciliations,alerts TO "${runtimeRole}";
