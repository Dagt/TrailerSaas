package com.trailersaas.documents.internal;

import static com.trailersaas.documents.internal.DocumentContracts.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DocumentRepository {
  record Row(UUID id,UUID tenant,String key,Type type,String contentType,Long size,String checksum,
      Status status,UUID uploader,Source source,Instant created,Instant uploaded) {}
  private final JdbcTemplate jdbc;
  DocumentRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  Row find(UUID tenant,UUID id,boolean lock) {
    var rows=jdbc.query("SELECT * FROM documents WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),
        (r,n)->row(r),tenant,id);
    if(rows.isEmpty()) throw DocumentFailure.missing();return rows.getFirst();
  }
  private Row row(ResultSet r) throws SQLException {
    return new Row(r.getObject("id",UUID.class),r.getObject("tenant_id",UUID.class),r.getString("object_key"),
        Type.valueOf(r.getString("document_type")),r.getString("content_type"),r.getObject("size_bytes",Long.class),
        r.getString("checksum_sha256"),Status.valueOf(r.getString("upload_status")),r.getObject("uploaded_by_user_id",UUID.class),
        Source.valueOf(r.getString("source_type")),r.getTimestamp("created_at").toInstant(),
        r.getTimestamp("uploaded_at")==null?null:r.getTimestamp("uploaded_at").toInstant());
  }
  void insert(Row row) {
    jdbc.update("""
        INSERT INTO documents(id,tenant_id,object_key,document_type,content_type,size_bytes,checksum_sha256,
          upload_status,uploaded_by_user_id,source_type,created_at)
        VALUES(?,?,?,?,?,?,?,'PENDING',?,'USER_ACTION',?)
        """,row.id(),row.tenant(),row.key(),row.type().name(),row.contentType(),row.size(),row.checksum(),row.uploader(),java.sql.Timestamp.from(row.created()));
  }
  void publish(Row row,String key,String sha,Instant time) {
    if(jdbc.update("""
        UPDATE documents SET object_key=?,checksum_sha256=?,upload_status='UPLOADED',uploaded_at=?
        WHERE tenant_id=? AND id=? AND upload_status='PENDING'
        """,key,sha,java.sql.Timestamp.from(time),row.tenant(),row.id())!=1) throw DocumentFailure.conflict("RESOURCE_CONFLICT");
  }
  List<Link> links(UUID tenant,UUID document) {
    var links=jdbc.query("SELECT id,entity_type,entity_id,created_at FROM document_links WHERE tenant_id=? AND document_id=? ORDER BY created_at,id LIMIT 51",
        (r,n)->new Link(r.getObject(1,UUID.class),Target.valueOf(r.getString(2)),r.getObject(3,UUID.class),r.getTimestamp(4).toInstant()),tenant,document);
    // Do not truncate before authorization: a hidden later link must never grant partial visibility.
    if(links.size()>50) throw DocumentFailure.missing();return links;
  }
  void link(UUID tenant,UUID document,LinkRequest request) {
    jdbc.update("""
        INSERT INTO document_links(id,tenant_id,document_id,entity_type,entity_id,created_at)
        VALUES(?,?,?,?,?,now()) ON CONFLICT(tenant_id,document_id,entity_type,entity_id) DO NOTHING
        """,UUID.randomUUID(),tenant,document,request.entityType().name(),request.entityId());
  }
  List<UUID> oldPending(UUID tenant,Instant cutoff,int limit) {
    return jdbc.query("""
        SELECT d.id FROM documents d WHERE tenant_id=? AND upload_status='PENDING' AND created_at<?
        AND NOT EXISTS(SELECT 1 FROM document_links l WHERE l.tenant_id=d.tenant_id AND l.document_id=d.id)
        ORDER BY created_at,id LIMIT ?
        """,(r,n)->r.getObject(1,UUID.class),tenant,java.sql.Timestamp.from(cutoff),limit);
  }
  static Metadata metadata(Row row,List<Link> links) {
    return new Metadata(row.id(),row.type(),row.contentType(),row.size(),row.checksum(),row.status(),row.created(),row.uploaded(),links);
  }
}
