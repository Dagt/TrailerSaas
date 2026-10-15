-- #59: bounded corrections of uncommitted Customer requests, not physical history.
CREATE TABLE order_request_revisions (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    order_id uuid NOT NULL,
    actor_user_id uuid NOT NULL REFERENCES users(id),
    order_version bigint NOT NULL CHECK(order_version > 0),
    previous_request jsonb NOT NULL CHECK(jsonb_typeof(previous_request)='object'),
    replacement_request jsonb NOT NULL CHECK(jsonb_typeof(replacement_request)='object'),
    recorded_at timestamptz NOT NULL,
    FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id),
    UNIQUE(tenant_id,order_id,order_version)
);
ALTER TABLE order_request_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_request_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY order_request_revisions_tenant ON order_request_revisions TO "${runtimeRole}"
    USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON order_request_revisions TO "${runtimeRole}";
-- Existing SELECT/INSERT remain; grant DELETE solely on these requested children.
-- This guard also rejects direct runtime deletion after any planning/physical commitment.
CREATE FUNCTION "${flyway:defaultSchema}".guard_unallocated_order_request_delete() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog,"${flyway:defaultSchema}",pg_temp AS $$
DECLARE current_status varchar;
BEGIN
    SELECT status INTO current_status FROM "${flyway:defaultSchema}".orders WHERE tenant_id=OLD.tenant_id AND id=OLD.order_id FOR UPDATE;
    IF current_status IS DISTINCT FROM 'OPEN'
        OR EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".manifest_orders WHERE tenant_id=OLD.tenant_id AND order_id=OLD.order_id)
        OR EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".trip_stop_orders WHERE tenant_id=OLD.tenant_id AND order_id=OLD.order_id)
        OR EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".order_fulfillment_events WHERE tenant_id=OLD.tenant_id AND order_id=OLD.order_id)
    THEN RAISE EXCEPTION 'Order request is committed' USING ERRCODE='23514'; END IF;
    RETURN OLD;
END $$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".guard_unallocated_order_request_delete() FROM PUBLIC;
CREATE TRIGGER order_cargo_unallocated_delete BEFORE DELETE ON "${flyway:defaultSchema}".order_cargo_lines FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".guard_unallocated_order_request_delete();
CREATE TRIGGER order_stops_unallocated_delete BEFORE DELETE ON "${flyway:defaultSchema}".order_stops FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".guard_unallocated_order_request_delete();
GRANT DELETE ON order_cargo_lines,order_stops TO "${runtimeRole}";
