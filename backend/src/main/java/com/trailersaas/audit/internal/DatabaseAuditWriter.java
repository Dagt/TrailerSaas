package com.trailersaas.audit.internal;

import com.trailersaas.audit.AuditWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation=Propagation.MANDATORY)
class DatabaseAuditWriter implements AuditWriter {
    private final AuditStore store;
    private final JsonMapper json=JsonMapper.builder().build();
    DatabaseAuditWriter(AuditStore store) { this.store=store; }
    public void record(UUID tenant,Actor actor,Action action,UUID entityId,Map<String,Object> before,Map<String,Object> after,String reason) {
        store.requireContext(tenant,true);
        if(actor==null || actor.type()==null || actor.source()==null || action==null)throw AuditCatalog.invalid();
        boolean human=actor.type()==ActorType.HUMAN_USER;
        boolean valid=switch(actor.type()) {
            case HUMAN_USER -> actor.userId()!=null && Set.of(SourceType.USER_ACTION,SourceType.MOBILE_AUTOMATION).contains(actor.source());
            case SYSTEM -> actor.userId()==null && actor.source()==SourceType.SYSTEM_PROCESS;
            case INTEGRATION -> actor.userId()==null && actor.source()==SourceType.EXTERNAL_INTEGRATION;
        };
        if(!valid || reason!=null && (reason.isBlank() || reason.length()>4000))throw AuditCatalog.invalid();
        var old=AuditCatalog.safe(action,before);var next=AuditCatalog.safe(action,after);
        if(next==null || (action.creation() ? old!=null : old==null))throw AuditCatalog.invalid();
        store.requireEntity(tenant,action.entityType(),entityId);
        UUID membership=human?store.membership(tenant,actor.userId()):null;
        if(Objects.equals(old,next))return; // Normalized no-op; never update an existing Audit record.
        var args=new HashMap<String,Object>();args.put("id",UUID.randomUUID());args.put("tenant",tenant);args.put("actor",actor.type().name());args.put("source",actor.source().name());args.put("user",actor.userId());args.put("membership",membership);args.put("action",action.name());args.put("entity",action.entityType().name());args.put("entityId",entityId);args.put("before",encode(old));args.put("after",encode(next));args.put("reason",reason);args.put("key",key(action,old,next));
        var inserted=store.db.queryForList("""
            insert into audit_events(id,tenant_id,actor_type,actor_user_id,actor_membership_id,source_type,
                action_code,entity_type,entity_id,before_data,after_data,reason)
            values(:id,:tenant,:actor,:user,:membership,:source,:action,:entity,:entityId,cast(:before as jsonb),cast(:after as jsonb),:reason)
            on conflict(tenant_id,entity_type,entity_id,action_code,change_key) do nothing returning id
            """,args,UUID.class);
        if(inserted.isEmpty() && !Boolean.TRUE.equals(store.db.queryForObject("""
            select actor_type=:actor and actor_user_id is not distinct from cast(:user as uuid)
                and actor_membership_id is not distinct from cast(:membership as uuid) and source_type=:source
                and before_data is not distinct from cast(:before as jsonb) and after_data=cast(:after as jsonb)
                and reason is not distinct from cast(:reason as text)
            from audit_events where tenant_id=:tenant and entity_type=:entity and entity_id=:entityId and action_code=:action and change_key=:key
            """,args,Boolean.class)))throw new IllegalStateException("Conflicting audit change identity");
    }
    private String encode(Map<String,Object> data) {
        if(data==null)return null;
        var encoded=json.writeValueAsString(data);
        if(encoded.getBytes(StandardCharsets.UTF_8).length>65536)throw AuditCatalog.invalid();return encoded;
    }
    private static String key(Action action,Map<String,Object> before,Map<String,Object> after) {
        if(action==Action.CUSTOMER_SITE_ASSOCIATED || action==Action.CUSTOMER_SITE_DISASSOCIATED) {
            var id=after.get("associationId");if(id==null)id=before.get("associationId");
            if(id==null)throw AuditCatalog.invalid();return "association:"+id;
        }
        if(Set.of(EntityType.INVITATION,EntityType.TRAILER_QR_IDENTITY,EntityType.TENANT_MEMBERSHIP).contains(action.entityType()))return "once";
        var v=after.get("version");if(!(v instanceof Number n) || !v.toString().matches("0|[1-9][0-9]*") || n.longValue()<0)throw AuditCatalog.invalid();return "version:"+v;
    }
}
