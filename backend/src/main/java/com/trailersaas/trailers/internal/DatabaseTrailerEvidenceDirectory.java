package com.trailersaas.trailers.internal;

import com.trailersaas.trailers.TrailerEvidenceDirectory;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository @Transactional(propagation=Propagation.MANDATORY)
class DatabaseTrailerEvidenceDirectory implements TrailerEvidenceDirectory {
  private final JdbcTemplate jdbc;
  DatabaseTrailerEvidenceDirectory(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public boolean lockExists(UUID tenant,UUID trailer) {
    return find(tenant,trailer," FOR SHARE");
  }
  public boolean exists(UUID tenant,UUID trailer) { return find(tenant,trailer,""); }
  private boolean find(UUID tenant,UUID trailer,String lock) {
    return !jdbc.query("SELECT id FROM trailers WHERE tenant_id=? AND id=?"+lock,(r,n)->r.getObject(1,UUID.class),tenant,trailer).isEmpty();
  }
}
