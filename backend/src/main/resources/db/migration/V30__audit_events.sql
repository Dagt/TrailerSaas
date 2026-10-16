-- #61: administrative business audit; specialized operational histories stay intact.
-- Accepted hardening E: retain the actual historical User/membership/Tenant identity.
ALTER TABLE tenant_memberships ADD CONSTRAINT tenant_memberships_audit_actor_key UNIQUE(tenant_id,id,user_id);

CREATE FUNCTION "${flyway:defaultSchema}".audit_entity_type(action text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE SET search_path=pg_catalog AS $$
SELECT CASE action
WHEN 'TENANT_SETTINGS_UPDATED' THEN 'TENANT_SETTINGS'
WHEN 'CUSTOMER_CREATED' THEN 'CUSTOMER'
WHEN 'CUSTOMER_UPDATED' THEN 'CUSTOMER'
WHEN 'SITE_CREATED' THEN 'PHYSICAL_SITE'
WHEN 'SITE_UPDATED' THEN 'PHYSICAL_SITE'
WHEN 'LOCATION_CREATED' THEN 'OPERATIONAL_LOCATION'
WHEN 'LOCATION_UPDATED' THEN 'OPERATIONAL_LOCATION'
WHEN 'DRIVER_CREATED' THEN 'DRIVER'
WHEN 'DRIVER_UPDATED' THEN 'DRIVER'
WHEN 'DRIVER_USER_LINKED' THEN 'DRIVER'
WHEN 'DRIVER_USER_UNLINKED' THEN 'DRIVER'
WHEN 'TRACTOR_CREATED' THEN 'TRACTOR'
WHEN 'TRACTOR_UPDATED' THEN 'TRACTOR'
WHEN 'TRAILER_CREATED' THEN 'TRAILER'
WHEN 'TRAILER_UPDATED' THEN 'TRAILER'
WHEN 'TRAILER_QR_ISSUED' THEN 'TRAILER_QR_IDENTITY'
WHEN 'TRAILER_QR_REVOKED' THEN 'TRAILER_QR_IDENTITY'
WHEN 'INVITATION_CREATED' THEN 'INVITATION'
WHEN 'INVITATION_EXPIRED' THEN 'INVITATION'
WHEN 'INVITATION_REVOKED' THEN 'INVITATION'
WHEN 'INVITATION_ACCEPTED' THEN 'INVITATION'
WHEN 'MEMBERSHIP_CREATED' THEN 'TENANT_MEMBERSHIP'
WHEN 'CUSTOMER_SITE_ASSOCIATED' THEN 'CUSTOMER'
WHEN 'CUSTOMER_SITE_DISASSOCIATED' THEN 'CUSTOMER'
ELSE NULL END $$;

-- A second barrier against accidental raw request/entity serialization. No unknown/nested secret keys.
CREATE FUNCTION "${flyway:defaultSchema}".audit_snapshot_valid(action text, data jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE SET search_path=pg_catalog AS $$
DECLARE allowed text[]; k text; v jsonb; r jsonb;
BEGIN
    IF data IS NULL THEN RETURN true; END IF;
    IF jsonb_typeof(data)<>'object' OR data='{}'::jsonb OR octet_length(data::text)>65536 THEN RETURN false; END IF;
    allowed:=CASE action
WHEN 'TENANT_SETTINGS_UPDATED' THEN ARRAY['staleTrailerThresholdHours','defaultTimezone','version']::text[]
WHEN 'CUSTOMER_CREATED' THEN ARRAY['name','status','version']::text[]
WHEN 'CUSTOMER_UPDATED' THEN ARRAY['name','status','version']::text[]
WHEN 'SITE_CREATED' THEN ARRAY['name','address','timezone','latitude','longitude','geofenceRadiusM','active','version']::text[]
WHEN 'SITE_UPDATED' THEN ARRAY['name','address','timezone','latitude','longitude','geofenceRadiusM','active','version']::text[]
WHEN 'LOCATION_CREATED' THEN ARRAY['siteId','customerId','name','type','code','active','version']::text[]
WHEN 'LOCATION_UPDATED' THEN ARRAY['siteId','customerId','name','type','code','active','version']::text[]
WHEN 'DRIVER_CREATED' THEN ARRAY['name','userId','active','licenseNumberPresent','medicalCertificatePresent','redactedChanges','version']::text[]
WHEN 'DRIVER_UPDATED' THEN ARRAY['name','userId','active','licenseNumberPresent','medicalCertificatePresent','redactedChanges','version']::text[]
WHEN 'DRIVER_USER_LINKED' THEN ARRAY['name','userId','active','licenseNumberPresent','medicalCertificatePresent','redactedChanges','version']::text[]
WHEN 'DRIVER_USER_UNLINKED' THEN ARRAY['name','userId','active','licenseNumberPresent','medicalCertificatePresent','redactedChanges','version']::text[]
WHEN 'TRACTOR_CREATED' THEN ARRAY['operationalNumber','active','version']::text[]
WHEN 'TRACTOR_UPDATED' THEN ARRAY['operationalNumber','active','version']::text[]
WHEN 'TRAILER_CREATED' THEN ARRAY['operationalNumber','designatedCustomerId','trailerType','usagePurpose','active','capabilityNotesPresent','redactedChanges','version']::text[]
WHEN 'TRAILER_UPDATED' THEN ARRAY['operationalNumber','designatedCustomerId','trailerType','usagePurpose','active','capabilityNotesPresent','redactedChanges','version']::text[]
WHEN 'TRAILER_QR_ISSUED' THEN ARRAY['trailerId','active','issuedAt','revokedAt']::text[]
WHEN 'TRAILER_QR_REVOKED' THEN ARRAY['trailerId','active','issuedAt','revokedAt']::text[]
WHEN 'INVITATION_CREATED' THEN ARRAY['status','expiresAt','roles','membershipId']::text[]
WHEN 'INVITATION_EXPIRED' THEN ARRAY['status','expiresAt','roles','membershipId']::text[]
WHEN 'INVITATION_REVOKED' THEN ARRAY['status','expiresAt','roles','membershipId']::text[]
WHEN 'INVITATION_ACCEPTED' THEN ARRAY['status','expiresAt','roles','membershipId']::text[]
WHEN 'MEMBERSHIP_CREATED' THEN ARRAY['userId','status','roles']::text[]
WHEN 'CUSTOMER_SITE_ASSOCIATED' THEN ARRAY['siteId','associationId','associated']::text[]
WHEN 'CUSTOMER_SITE_DISASSOCIATED' THEN ARRAY['siteId','associationId','associated']::text[]
    ELSE NULL END;
    IF allowed IS NULL THEN RETURN false; END IF;
    FOR k,v IN SELECT * FROM jsonb_each(data) LOOP
        IF NOT(k=ANY(allowed)) THEN RETURN false; END IF;
        IF k='address' AND v<>'null'::jsonb THEN
            IF jsonb_typeof(v)<>'object' THEN RETURN false; END IF;
            IF EXISTS(SELECT 1 FROM jsonb_each(v) a WHERE a.key<>ALL(ARRAY['line1','line2','city','stateRegion','postalCode','countryCode']) OR jsonb_typeof(a.value) NOT IN('string','null')) THEN RETURN false; END IF;
        ELSIF k='roles' THEN
            IF jsonb_typeof(v)<>'array' THEN RETURN false; END IF;
            FOR r IN SELECT * FROM jsonb_array_elements(v) LOOP
                IF jsonb_typeof(r)<>'object' THEN RETURN false; END IF;
                IF NOT(r ?& ARRAY['roleCode','scopeType','customerId']) OR EXISTS(SELECT 1 FROM jsonb_object_keys(r) x WHERE x<>ALL(ARRAY['roleCode','scopeType','customerId'])) THEN RETURN false; END IF;
                IF COALESCE(r->>'roleCode','') NOT IN('TENANT_ADMIN','TRANSPORT_OPERATIONS','CUSTOMER_DISPATCHER','YARD_SPOTTER','DRIVER') THEN RETURN false; END IF;
                IF r->>'roleCode'='CUSTOMER_DISPATCHER' THEN
                    IF (r->>'scopeType') IS DISTINCT FROM 'CUSTOMER' OR NOT COALESCE(r->>'customerId' ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',false) THEN RETURN false; END IF;
                ELSIF (r->>'scopeType') IS DISTINCT FROM 'TENANT' OR r->'customerId'<>'null'::jsonb THEN RETURN false;
                END IF;
            END LOOP;
        ELSIF k='redactedChanges' THEN
            IF jsonb_typeof(v)<>'array' THEN RETURN false; END IF;
            IF EXISTS(SELECT 1 FROM jsonb_array_elements(v) x WHERE jsonb_typeof(x)<>'string' OR (x #>> '{}')<>ALL(CASE WHEN action LIKE 'DRIVER_%' THEN ARRAY['licenseNumber','medicalCertificateExpires'] ELSE ARRAY['capabilityNotes'] END)) THEN RETURN false; END IF;
        ELSIF jsonb_typeof(v) IN('object','array') THEN RETURN false;
        END IF;
        IF k='version' AND (jsonb_typeof(v)<>'number' OR NOT(v::text ~ '^(0|[1-9][0-9]*)$') OR (v::text)::numeric>9223372036854775807) THEN RETURN false; END IF;
        IF k IN('active','associated','licenseNumberPresent','medicalCertificatePresent','capabilityNotesPresent') AND jsonb_typeof(v)<>'boolean' THEN RETURN false; END IF;
        IF k LIKE '%Id' AND v<>'null'::jsonb AND (jsonb_typeof(v)<>'string' OR NOT((v #>> '{}') ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')) THEN RETURN false; END IF;
    END LOOP;
    RETURN true;
END $$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".audit_entity_type(text), "${flyway:defaultSchema}".audit_snapshot_valid(text,jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION "${flyway:defaultSchema}".audit_entity_type(text), "${flyway:defaultSchema}".audit_snapshot_valid(text,jsonb) TO "${runtimeRole}";

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    actor_type varchar NOT NULL,
    actor_user_id uuid NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_membership_id uuid NULL,
    source_type varchar NOT NULL,
    action_code varchar NOT NULL,
    entity_type varchar NOT NULL,
    entity_id uuid NOT NULL,
    before_data jsonb NULL,
    after_data jsonb NULL,
    reason text NULL CHECK(reason IS NULL OR (length(reason)<=4000 AND reason !~ '^[[:space:]]*$')),
    occurred_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    -- Domain version/association identity, not another command ledger or fabricated mobile operation.
    change_key varchar(120) GENERATED ALWAYS AS (CASE
        WHEN action_code IN('CUSTOMER_SITE_ASSOCIATED','CUSTOMER_SITE_DISASSOCIATED') THEN 'association:'||COALESCE(after_data->>'associationId',before_data->>'associationId')
        WHEN entity_type IN('INVITATION','TRAILER_QR_IDENTITY','TENANT_MEMBERSHIP') THEN 'once'
        ELSE 'version:'||(after_data->>'version') END) STORED NOT NULL,
    FOREIGN KEY(tenant_id,actor_membership_id,actor_user_id) REFERENCES tenant_memberships(tenant_id,id,user_id) ON DELETE RESTRICT,
    CHECK((actor_type='HUMAN_USER' AND actor_user_id IS NOT NULL AND actor_membership_id IS NOT NULL AND source_type IN('USER_ACTION','MOBILE_AUTOMATION'))
       OR (actor_type='SYSTEM' AND actor_user_id IS NULL AND actor_membership_id IS NULL AND source_type='SYSTEM_PROCESS')
       OR (actor_type='INTEGRATION' AND actor_user_id IS NULL AND actor_membership_id IS NULL AND source_type='EXTERNAL_INTEGRATION')),
    CHECK("${flyway:defaultSchema}".audit_entity_type(action_code) IS NOT NULL AND entity_type="${flyway:defaultSchema}".audit_entity_type(action_code)),
    CHECK("${flyway:defaultSchema}".audit_snapshot_valid(action_code,before_data) AND "${flyway:defaultSchema}".audit_snapshot_valid(action_code,after_data)),
    CHECK(after_data IS NOT NULL AND (CASE WHEN action_code LIKE '%_CREATED' OR action_code='TRAILER_QR_ISSUED' THEN before_data IS NULL ELSE before_data IS NOT NULL END)),
    CHECK(before_data IS NULL OR before_data<>after_data),
    UNIQUE(tenant_id,entity_type,entity_id,action_code,change_key)
);
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_events_tenant ON audit_events TO "${runtimeRole}"
    USING(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL)
    WITH CHECK(tenant_id=NULLIF(current_setting('app.current_tenant_id',true),'')::uuid AND NULLIF(current_setting('app.current_user_id',true),'') IS NULL);
GRANT SELECT,INSERT ON audit_events TO "${runtimeRole}";

CREATE FUNCTION "${flyway:defaultSchema}".audit_events_immutable() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN RAISE EXCEPTION 'Audit history is immutable' USING ERRCODE='23514'; END $$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".audit_events_immutable() FROM PUBLIC;
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".audit_events_immutable();

CREATE INDEX audit_events_tenant_time ON audit_events(tenant_id,occurred_at DESC,id DESC);
CREATE INDEX audit_events_entity_time ON audit_events(tenant_id,entity_type,entity_id,occurred_at DESC,id DESC);
CREATE INDEX audit_events_actor_time ON audit_events(tenant_id,actor_user_id,occurred_at DESC,id DESC);
CREATE INDEX audit_events_membership_time ON audit_events(tenant_id,actor_membership_id,occurred_at DESC,id DESC);
CREATE INDEX audit_events_action_time ON audit_events(tenant_id,action_code,occurred_at DESC,id DESC);
