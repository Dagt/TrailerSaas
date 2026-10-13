-- #49: observations do not mutate Trailer state; accepted evidence belongs to a real OOS event.
CREATE TABLE yard_discrepancy_reports (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  trailer_id uuid NOT NULL,
  site_id uuid NOT NULL,
  recorded_location_id uuid NULL,
  reported_location_id uuid NOT NULL,
  observed_trailer_version bigint NOT NULL CHECK(observed_trailer_version>=0),
  actor_user_id uuid NOT NULL REFERENCES users(id),
  reason text NOT NULL CHECK(length(reason)<=4000 AND reason ~ '[^[:space:]]'),
  occurred_at timestamptz NOT NULL,
  recorded_at timestamptz NOT NULL,
  client_operation_id uuid NOT NULL,
  UNIQUE(tenant_id,id),
  UNIQUE(tenant_id,client_operation_id),
  FOREIGN KEY(tenant_id,trailer_id) REFERENCES trailers(tenant_id,id),
  FOREIGN KEY(tenant_id,site_id) REFERENCES physical_sites(tenant_id,id),
  FOREIGN KEY(tenant_id,recorded_location_id) REFERENCES operational_locations(tenant_id,id),
  FOREIGN KEY(tenant_id,site_id,reported_location_id) REFERENCES operational_locations(tenant_id,site_id,id),
  FOREIGN KEY(tenant_id,client_operation_id) REFERENCES processed_client_operations(tenant_id,client_operation_id),
  CHECK(recorded_location_id IS DISTINCT FROM reported_location_id),
  CHECK(occurred_at<=recorded_at)
);
CREATE INDEX yard_reports_review ON yard_discrepancy_reports(tenant_id,recorded_at,id);

CREATE TABLE trailer_oos_evidence (
  tenant_id uuid NOT NULL,
  trailer_id uuid NOT NULL,
  event_id uuid NOT NULL,
  document_id uuid NOT NULL,
  actor_user_id uuid NOT NULL REFERENCES users(id),
  client_operation_id uuid NOT NULL,
  PRIMARY KEY(tenant_id,event_id,document_id),
  UNIQUE(tenant_id,document_id),
  FOREIGN KEY(tenant_id,trailer_id) REFERENCES trailers(tenant_id,id),
  FOREIGN KEY(tenant_id,event_id) REFERENCES trailer_movement_events(tenant_id,id),
  FOREIGN KEY(tenant_id,document_id) REFERENCES documents(tenant_id,id),
  FOREIGN KEY(tenant_id,client_operation_id) REFERENCES processed_client_operations(tenant_id,client_operation_id)
);

CREATE FUNCTION guard_yard_history() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
  RAISE EXCEPTION 'Yard operational history is append-only' USING ERRCODE='42501';
END $$;
REVOKE ALL ON FUNCTION guard_yard_history() FROM PUBLIC;
CREATE TRIGGER yard_reports_immutable BEFORE UPDATE OR DELETE ON yard_discrepancy_reports
  FOR EACH ROW EXECUTE FUNCTION guard_yard_history();
CREATE TRIGGER trailer_oos_evidence_immutable BEFORE UPDATE OR DELETE ON trailer_oos_evidence
  FOR EACH ROW EXECUTE FUNCTION guard_yard_history();

CREATE FUNCTION guard_trailer_oos_evidence() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.trailer_movement_events e JOIN public.documents d ON d.tenant_id=e.tenant_id
    WHERE e.tenant_id=NEW.tenant_id AND e.id=NEW.event_id AND e.trailer_id=NEW.trailer_id
      AND e.event_type='MARKED_OOS' AND e.actor_type='HUMAN_USER' AND e.source_type='USER_ACTION'
      AND e.actor_user_id=NEW.actor_user_id AND e.client_operation_id=NEW.client_operation_id
      AND e.xmin::text::numeric=mod(pg_current_xact_id()::text::numeric,4294967296)
      AND d.id=NEW.document_id AND d.document_type='DAMAGE_PHOTO' AND d.upload_status='UPLOADED'
      AND d.uploaded_by_user_id=NEW.actor_user_id
      AND EXISTS(SELECT 1 FROM public.document_links l WHERE l.tenant_id=d.tenant_id AND l.document_id=d.id
                 AND l.entity_type='TRAILER' AND l.entity_id=NEW.trailer_id)
      AND NOT EXISTS(SELECT 1 FROM public.document_links l WHERE l.tenant_id=d.tenant_id AND l.document_id=d.id
                     AND (l.entity_type<>'TRAILER' OR l.entity_id<>NEW.trailer_id))
  ) THEN RAISE EXCEPTION 'OOS evidence does not match current operation' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION guard_trailer_oos_evidence() FROM PUBLIC;
CREATE TRIGGER trailer_oos_evidence_context BEFORE INSERT ON trailer_oos_evidence
  FOR EACH ROW EXECUTE FUNCTION guard_trailer_oos_evidence();

ALTER TABLE yard_discrepancy_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE trailer_oos_evidence ENABLE ROW LEVEL SECURITY;
CREATE POLICY yard_discrepancy_reports_tenant ON yard_discrepancy_reports TO "${runtimeRole}"
  USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
  WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY trailer_oos_evidence_tenant ON trailer_oos_evidence TO "${runtimeRole}"
  USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
  WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON yard_discrepancy_reports,trailer_oos_evidence TO "${runtimeRole}";
