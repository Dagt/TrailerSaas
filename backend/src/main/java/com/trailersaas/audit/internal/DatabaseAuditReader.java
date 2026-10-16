package com.trailersaas.audit.internal;

import com.trailersaas.ReadCursor;
import com.trailersaas.audit.*;
import com.trailersaas.audit.AuditWriter.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(propagation=Propagation.MANDATORY,readOnly=true)
class DatabaseAuditReader implements AuditReader {
    private final AuditStore store;
    private final JsonMapper json=JsonMapper.builder().build();
    DatabaseAuditReader(AuditStore store) { this.store=store; }
    public Page list(UUID tenant,UUID user,Map<String,String> raw) {
        store.requireContext(tenant,false);
        if(user==null || !Set.of("actorUserId","actionCode","entityType","entityId","from","to","limit","cursor").containsAll(raw.keySet()))throw AuditQueryFailure.invalid();
        int limit=50;
        if(raw.containsKey("limit")){var n=raw.get("limit");if(!n.matches("[1-9][0-9]{0,2}") || (limit=Integer.parseInt(n))>100)throw AuditQueryFailure.invalid();}
        var args=new HashMap<String,Object>();args.put("tenant",tenant);args.put("limit",limit+1);
        String where=" where tenant_id=:tenant";
        try {
            for(var field:List.of("actorUserId","entityId"))if(raw.containsKey(field)){UUID id=UUID.fromString(raw.get(field));if(!id.toString().equals(raw.get(field)))throw AuditQueryFailure.invalid();args.put(field,id);where+=" and "+(field.equals("entityId")?"entity_id":"actor_user_id")+"=:"+field;}
            for(var field:List.of("actionCode","entityType"))if(raw.containsKey(field)){String v=field.equals("actionCode")?Action.valueOf(raw.get(field)).name():EntityType.valueOf(raw.get(field)).name();args.put(field,v);where+=" and "+(field.equals("actionCode")?"action_code":"entity_type")+"=:"+field;}
            Instant from=raw.containsKey("from")?Instant.parse(raw.get("from")):null,to=raw.containsKey("to")?Instant.parse(raw.get("to")):null;
            if(from!=null && to!=null && from.isAfter(to))throw AuditQueryFailure.invalid();
            if(from!=null){where+=" and occurred_at>=:from";args.put("from",Timestamp.from(from));}
            if(to!=null){where+=" and occurred_at<=:to";args.put("to",Timestamp.from(to));}
        }catch(IllegalArgumentException | java.time.DateTimeException e){throw AuditQueryFailure.invalid();}
        var filters=new TreeMap<>(raw);String cursor=filters.remove("cursor");filters.remove("limit");
        String context=user+"|"+tenant+"|audit-events|"+filters;
        List<UUID> position;
        try{position=ReadCursor.decode(cursor,context,1);}catch(IllegalArgumentException e){throw AuditQueryFailure.invalid();}
        if(!position.isEmpty()){
            args.put("anchor",position.getFirst());var anchors=store.db.queryForList("select occurred_at from audit_events"+where+" and id=:anchor",args,Timestamp.class);
            if(anchors.isEmpty())throw AuditQueryFailure.invalid();args.put("at",anchors.getFirst());
            where+=" and (occurred_at,id)<(:at,:anchor)";
        }
        var rows=store.db.query("select * from audit_events"+where+" order by occurred_at desc,id desc limit :limit",args,(r,n)->event(r));
        boolean more=rows.size()>limit;var items=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));
        return new Page(items,more?ReadCursor.encode(context,items.getLast().id()):null);
    }
    public Event detail(UUID tenant,UUID id) {
        store.requireContext(tenant,false);
        var rows=store.db.query("select * from audit_events where tenant_id=:tenant and id=:id",Map.of("tenant",tenant,"id",id),(r,n)->event(r));
        if(rows.isEmpty())throw AuditQueryFailure.missing();return rows.getFirst();
    }
    private Event event(ResultSet r)throws SQLException {
        return new Event(r.getObject("id",UUID.class),new AuditReader.Actor(ActorType.valueOf(r.getString("actor_type")),r.getObject("actor_user_id",UUID.class),r.getObject("actor_membership_id",UUID.class)),SourceType.valueOf(r.getString("source_type")),Action.valueOf(r.getString("action_code")),EntityType.valueOf(r.getString("entity_type")),r.getObject("entity_id",UUID.class),data(r.getString("before_data")),data(r.getString("after_data")),r.getString("reason"),r.getTimestamp("occurred_at").toInstant());
    }
    @SuppressWarnings("unchecked") private Map<String,Object> data(String raw) { return raw==null?null:Collections.unmodifiableMap(json.readValue(raw,Map.class)); }
}
