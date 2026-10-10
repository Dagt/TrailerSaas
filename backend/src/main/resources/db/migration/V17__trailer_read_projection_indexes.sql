CREATE INDEX trailer_couplings_trailer_history
ON trailer_couplings (tenant_id, trailer_id, hooked_at DESC, id DESC);

CREATE INDEX load_manifests_trailer_lookup
ON load_manifests (tenant_id, trailer_id, updated_at DESC, id DESC)
WHERE trailer_id IS NOT NULL;
