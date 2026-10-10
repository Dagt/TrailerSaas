package com.trailersaas.identity.internal;

import com.trailersaas.identity.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation = Propagation.MANDATORY)
class DatabaseMobileOperationLedger implements MobileOperationLedger {
  private final EntityManager em;
  private final JsonMapper json;
  DatabaseMobileOperationLedger(EntityManager em, JsonMapper json) { this.em=em; this.json=json; }

  public <T> T execute(UUID tenant, UUID user, UUID key, String type, String hash,
      String entityType, Function<T,UUID> entityId, Class<T> responseType, Supplier<T> action) {
    if (!Boolean.TRUE.equals(em.createNativeQuery("select coalesce(NULLIF(current_setting('app.current_tenant_id',true),'')::uuid=:tenant and NULLIF(current_setting('app.current_user_id',true),'') is null,false)",Boolean.class).setParameter("tenant",tenant).getSingleResult()))
      throw new IllegalStateException("Pinned Tenant transaction required.");
    var claims=em.createNativeQuery("insert into processed_client_operations (id,tenant_id,client_operation_id,user_id,operation_type,request_hash,status,created_at) values(:id,:tenant,:key,:user,:type,:hash,'PROCESSING',:at) on conflict (tenant_id,client_operation_id) do nothing returning id",UUID.class)
      .setParameter("id",UUID.randomUUID()).setParameter("tenant",tenant).setParameter("key",key)
      .setParameter("user",user).setParameter("type",type).setParameter("hash",hash).setParameter("at",Instant.now()).getResultList();
    if(claims.isEmpty()) {
      var rows=em.createNativeQuery("select user_id,operation_type,request_hash,status,response_data::text from processed_client_operations where tenant_id=:tenant and client_operation_id=:key",Object[].class).setParameter("tenant",tenant).setParameter("key",key).getResultList();
      if(rows.isEmpty()) throw new IllegalStateException("Operation claim outcome unavailable.");
      var row=(Object[])rows.getFirst();
      if(!user.equals(row[0]) || !type.equals(row[1]) || !hash.equals(row[2]) || !"SUCCEEDED".equals(row[3]) || row[4]==null) throw new IdempotencyKeyReusedException();
      return json.readValue((String)row[4],responseType);
    }
    var result=action.get();
    em.flush();
    var count=em.createNativeQuery("update processed_client_operations set status='SUCCEEDED',processed_at=:at,result_entity_type=:type,result_entity_id=:entity,response_data=cast(:response as jsonb) where tenant_id=:tenant and id=:id and status='PROCESSING'")
      .setParameter("at",Instant.now()).setParameter("type",entityType).setParameter("entity",entityId.apply(result)).setParameter("response",json.writeValueAsString(result)).setParameter("tenant",tenant).setParameter("id",claims.getFirst()).executeUpdate();
    if(count!=1) throw new IllegalStateException("Operation claim completion failed.");
    return result;
  }
}
