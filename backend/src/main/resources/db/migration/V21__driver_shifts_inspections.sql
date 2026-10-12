CREATE TABLE shifts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    on_duty_at timestamptz NOT NULL,
    off_duty_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id,id),
    UNIQUE (tenant_id,id,driver_id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id),
    CHECK (off_duty_at IS NULL OR off_duty_at>=on_duty_at),
    CHECK (updated_at>=created_at)
);
CREATE UNIQUE INDEX shifts_one_open_driver ON shifts(tenant_id,driver_id) WHERE off_duty_at IS NULL;
CREATE INDEX shifts_driver_history ON shifts(tenant_id,driver_id,on_duty_at DESC,id DESC);

CREATE TABLE shift_corrections (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    shift_id uuid NOT NULL,
    field_name varchar NOT NULL CHECK (field_name IN ('on_duty_at','off_duty_at')),
    old_value timestamptz NULL,
    new_value timestamptz NULL,
    actor_user_id uuid NOT NULL REFERENCES users(id),
    reason text NOT NULL CHECK (reason ~ '[^[:space:]]'),
    corrected_at timestamptz NOT NULL,
    FOREIGN KEY (tenant_id,shift_id) REFERENCES shifts(tenant_id,id),
    CHECK (old_value IS DISTINCT FROM new_value)
);

CREATE TABLE inspections (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    tractor_id uuid NOT NULL,
    shift_id uuid NULL,
    trip_id uuid NULL,
    inspection_type varchar NOT NULL CHECK (inspection_type IN ('PRE_TRIP','POST_TRIP')),
    odometer numeric NULL CHECK (odometer>=0),
    damages text NULL,
    failures text NULL,
    comments text NULL,
    checklist_data jsonb NULL CHECK (checklist_data IS NULL OR jsonb_typeof(checklist_data)='object'),
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id,id),
    FOREIGN KEY (tenant_id,driver_id) REFERENCES drivers(tenant_id,id),
    FOREIGN KEY (tenant_id,tractor_id) REFERENCES tractors(tenant_id,id),
    FOREIGN KEY (tenant_id,shift_id,driver_id) REFERENCES shifts(tenant_id,id,driver_id),
    FOREIGN KEY (tenant_id,trip_id) REFERENCES trips(tenant_id,id)
);
CREATE INDEX inspections_shift_equipment ON inspections(tenant_id,shift_id,tractor_id,inspection_type,occurred_at DESC,id DESC);

ALTER TABLE shifts ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift_corrections ENABLE ROW LEVEL SECURITY;
ALTER TABLE inspections ENABLE ROW LEVEL SECURITY;
CREATE POLICY shifts_tenant ON shifts TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY shift_corrections_tenant ON shift_corrections TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
CREATE POLICY inspections_tenant ON inspections TO "${runtimeRole}"
    USING (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK (tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT,UPDATE ON shifts TO "${runtimeRole}";
GRANT SELECT,INSERT ON inspections,shift_corrections TO "${runtimeRole}";

ALTER TABLE document_links DROP CONSTRAINT document_links_entity_type_check;
ALTER TABLE document_links ADD CONSTRAINT document_links_entity_type_check CHECK (entity_type IN ('TRIP_STOP','TRIP','TRAILER','INSPECTION'));
-- One immutable inspection owner per photograph. Context Trip links may coexist.
CREATE UNIQUE INDEX document_links_inspection_photo ON document_links(tenant_id,document_id) WHERE entity_type='INSPECTION';
