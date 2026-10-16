-- Historical receipts did not retain accepted file identities. Do not guess a backfill.
ALTER TABLE delivery_evidence
    ADD COLUMN bol_document_id uuid NULL,
    ADD COLUMN delivery_photo_document_id uuid NULL,
    ADD CONSTRAINT delivery_evidence_bol_tenant_fk FOREIGN KEY (tenant_id,bol_document_id) REFERENCES documents(tenant_id,id),
    ADD CONSTRAINT delivery_evidence_photo_tenant_fk FOREIGN KEY (tenant_id,delivery_photo_document_id) REFERENCES documents(tenant_id,id),
    ADD CONSTRAINT delivery_evidence_files_mode CHECK (
        (bol_document_id IS NULL AND delivery_photo_document_id IS NULL)
        OR (bol_document_id IS NOT NULL AND (
            (evidence_mode='SIGNED_BOL' AND delivery_photo_document_id IS NULL)
            OR (evidence_mode='NO_RECEIVER_AVAILABLE' AND delivery_photo_document_id IS NOT NULL AND delivery_photo_document_id<>bol_document_id)
        ))
    );
-- Existing append-only runtime grants and Tenant RLS apply to these columns.
-- Runtime cannot rewrite historical receipts. Current recordDelivery always supplies validated IDs.
CREATE INDEX delivery_evidence_bol_lookup ON delivery_evidence(tenant_id,bol_document_id) WHERE bol_document_id IS NOT NULL;
CREATE INDEX delivery_evidence_photo_lookup ON delivery_evidence(tenant_id,delivery_photo_document_id) WHERE delivery_photo_document_id IS NOT NULL;
CREATE INDEX documents_published_history ON documents(tenant_id,uploaded_at DESC,id) WHERE upload_status='UPLOADED';
