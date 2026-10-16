package com.trailersaas.documents.internal;

import static com.trailersaas.documents.internal.CustomerReadContracts.*;

import com.trailersaas.ReadCursor;
import com.trailersaas.identity.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Read-only cross-domain SQL, like the Trailer read model. No foreign internal Java dependencies. */
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
class CustomerOperationalReads {
  private final NamedParameterJdbcTemplate db;
  private final CustomerActionAccess actions;
  CustomerOperationalReads(NamedParameterJdbcTemplate db,CustomerActionAccess actions) {this.db=db;this.actions=actions;}

  void require(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer,Capability scoped,Capability all) {
    var caps=actions.capabilities(actor,tenant,customer);
    if(!caps.contains(scoped)&&!caps.contains(all)) throw new com.trailersaas.documents.EvidenceValidationException(com.trailersaas.documents.EvidenceValidationException.Code.CAPABILITY_DENIED);
    if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from customers where tenant_id=:tenant and id=:customer and status='ACTIVE')",params(tenant,customer),Boolean.class))) throw DocumentFailure.missing();
  }
  private void orderAuthority(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer) {
    require(actor,tenant,customer,Capability.ORDER_VIEW_CUSTOMER_SCOPED,Capability.ORDER_VIEW_ALL);
  }
  void documentAuthority(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer) {
    require(actor,tenant,customer,Capability.DOCUMENT_VIEW_CUSTOMER_SCOPED,Capability.DOCUMENT_VIEW_ALL);
  }
  private static Map<String,Object> params(UUID tenant,UUID customer) {
    var result=new HashMap<String,Object>();result.put("tenant",tenant);result.put("customer",customer);return result;
  }
  static UUID uuid(String value) {
    try {var id=UUID.fromString(value);if(!id.toString().equals(value))throw DocumentFailure.invalid();return id;}
    catch(IllegalArgumentException e){throw DocumentFailure.invalid();}
  }
  private record Query(int limit,List<UUID> after,String context,Map<String,Object> args) {}
  private Query query(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer,String resource,Map<String,String> raw,Set<String> allowed,int parts) {
    if(!allowed.containsAll(raw.keySet()))throw DocumentFailure.invalid();
    int limit=20;
    if(raw.containsKey("limit")) {String n=raw.get("limit");if(!n.matches("[1-9][0-9]?")||(limit=Integer.parseInt(n))>50)throw DocumentFailure.invalid();}
    var filters=new TreeMap<>(raw);String cursor=filters.remove("cursor");
    String context=actor.userId()+"|"+tenant+"|"+customer+"|"+resource+"|"+filters;
    List<UUID> after;
    try {after=ReadCursor.decode(cursor,context,parts);}catch(IllegalArgumentException e){throw DocumentFailure.invalid();}
    var args=params(tenant,customer);args.put("limit",limit+1);
    return new Query(limit,after,context,args);
  }
  private static final String SOLE_STOP="""
    not exists(select 1 from trip_stop_orders ax join orders ox on ox.tenant_id=ax.tenant_id and ox.id=ax.order_id
      where ax.tenant_id=s.tenant_id and ax.trip_stop_id=s.id and ox.customer_id<>:customer)
    """;
  private static final String DELIVERIES="""
    from orders o join trip_stops s on s.tenant_id=o.tenant_id and exists(
      select 1 from trip_stop_orders a where a.tenant_id=o.tenant_id and a.order_id=o.id and a.trip_stop_id=s.id)
    join trips t on t.tenant_id=s.tenant_id and t.id=s.trip_id
    left join physical_sites site on site.tenant_id=s.tenant_id and site.id=s.site_id and exists(
      select 1 from customer_sites cs where cs.tenant_id=site.tenant_id and cs.site_id=site.id and cs.customer_id=:customer)
    left join trailers tr on tr.tenant_id=t.tenant_id and tr.id=t.planned_initial_trailer_id and tr.designated_customer_id=:customer
    left join delivery_evidence ev on ev.tenant_id=s.tenant_id and ev.trip_stop_id=s.id
    where o.tenant_id=:tenant and o.customer_id=:customer and s.stop_type='DELIVERY'
    """;
  private static final String DELIVERY_SELECT="""
    select o.id order_id,o.order_number,o.status order_status,s.id stop_id,t.id trip_id,
      coalesce(s.site_snapshot->>'name',site.name) destination,s.scheduled_at,s.status,s.arrived_at,s.service_started_at,
      s.completed_at,s.departed_at,tr.id trailer_id,tr.operational_number,
    """+"case when "+SOLE_STOP+" then ev.evidence_mode end evidence_mode,case when "+SOLE_STOP+" then ev.completed_at end accepted_at,case when "+SOLE_STOP+" then ev.bol_document_id end bol_document_id ";

  Page<Delivery> deliveries(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer,Map<String,String> raw) {
    orderAuthority(actor,tenant,customer);
    var q=query(actor,tenant,customer,"deliveries",raw,Set.of("cursor","limit","status","active","orderId"),2);
    String filter="";
    if(raw.containsKey("status")) {if(!Set.of("PENDING","EN_ROUTE","ARRIVED","IN_SERVICE","COMPLETED","CANCELLED").contains(raw.get("status")))throw DocumentFailure.invalid();filter+=" and s.status=:status";q.args.put("status",raw.get("status"));}
    if(raw.containsKey("active")){if(!Set.of("true","false").contains(raw.get("active")))throw DocumentFailure.invalid();filter+=" and s.status "+(raw.get("active").equals("true")?"in":"not in")+" ('PENDING','EN_ROUTE','ARRIVED','IN_SERVICE')";}
    if(raw.containsKey("orderId")){filter+=" and o.id=:order";q.args.put("order",uuid(raw.get("orderId")));}
    if(!q.after.isEmpty()){filter+=" and (o.id,s.id)>(:afterOrder,:afterStop)";q.args.put("afterOrder",q.after.get(0));q.args.put("afterStop",q.after.get(1));}
    var rows=db.query(DELIVERY_SELECT+DELIVERIES+filter+" order by o.id,s.id limit :limit",q.args,(r,n)->delivery(r,customer,List.of()));
    boolean more=rows.size()>q.limit;var page=rows.subList(0,Math.min(q.limit,rows.size()));
    // At most one bulk document query for the whole page, never one query per row.
    var caps=actions.capabilities(actor,tenant,customer);
    var docs=(caps.contains(Capability.DOCUMENT_VIEW_CUSTOMER_SCOPED)||caps.contains(Capability.DOCUMENT_VIEW_ALL))?documentsFor(tenant,customer,page):List.<Document>of();
    var items=page.stream().map(d->withDocuments(d,docs.stream().filter(f->f.orderId().equals(d.orderId())&&f.stopId().equals(d.stopId())).toList())).toList();
    return new Page<>(customer,items,more?ReadCursor.encode(q.context,items.getLast().orderId(),items.getLast().stopId()):null);
  }
  Delivery delivery(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer,UUID order,UUID stop,Map<String,String> raw) {
    if(!raw.isEmpty())throw DocumentFailure.invalid();orderAuthority(actor,tenant,customer);
    var args=params(tenant,customer);args.put("order",order);args.put("stop",stop);
    var rows=db.query(DELIVERY_SELECT+DELIVERIES+" and o.id=:order and s.id=:stop",args,(r,n)->delivery(r,customer,List.of()));
    if(rows.isEmpty())throw DocumentFailure.missing();
    var caps=actions.capabilities(actor,tenant,customer);
    var docs=(caps.contains(Capability.DOCUMENT_VIEW_CUSTOMER_SCOPED)||caps.contains(Capability.DOCUMENT_VIEW_ALL))?documentsFor(tenant,customer,rows):List.<Document>of();
    return withDocuments(rows.getFirst(),docs);
  }
  private static Delivery delivery(ResultSet r,UUID customer,List<Document> files)throws SQLException {
    UUID trailer=r.getObject("trailer_id",UUID.class);
    return new Delivery(customer,r.getObject("order_id",UUID.class),r.getString("order_number"),r.getString("order_status"),r.getObject("stop_id",UUID.class),r.getObject("trip_id",UUID.class),r.getString("destination"),time(r,"scheduled_at"),r.getString("status"),time(r,"arrived_at"),time(r,"service_started_at"),time(r,"completed_at"),time(r,"departed_at"),trailer==null?null:new Trailer(trailer,r.getString("operational_number")),r.getString("evidence_mode"),time(r,"accepted_at"),r.getObject("bol_document_id")!=null,files);
  }
  private static Delivery withDocuments(Delivery d,List<Document> files){return new Delivery(d.customerId(),d.orderId(),d.orderNumber(),d.orderStatus(),d.stopId(),d.tripId(),d.destination(),d.scheduledAt(),d.status(),d.arrivedAt(),d.serviceStartedAt(),d.completedAt(),d.departedAt(),d.expectedTrailer(),d.evidenceMode(),d.acceptedAt(),d.fileAssociationRecorded(),files);}
  private static Instant time(ResultSet r,String column)throws SQLException {var t=r.getTimestamp(column);return t==null?null:t.toInstant();}

  // Same all-linked-resource rule as DocumentResourceAccess, deliberately narrowed to ONE selected Customer.
  // A second allowed role/Customer does not widen this read context. No uploader/Driver override.
  private static final String DOCUMENTS="""
    from documents d join delivery_evidence ev on ev.tenant_id=d.tenant_id and (
      (ev.bol_document_id=d.id and d.document_type='BOL') or
      (ev.delivery_photo_document_id=d.id and d.document_type='DELIVERY_PHOTO'))
    join trip_stops s on s.tenant_id=ev.tenant_id and s.id=ev.trip_stop_id and s.stop_type='DELIVERY' and s.status='COMPLETED'
    join orders o on o.tenant_id=s.tenant_id and o.customer_id=:customer and exists(
      select 1 from trip_stop_orders a where a.tenant_id=s.tenant_id and a.trip_stop_id=s.id and a.order_id=o.id)
    where d.tenant_id=:tenant and d.upload_status='UPLOADED'
      and exists(select 1 from document_links l where l.tenant_id=d.tenant_id and l.document_id=d.id and l.entity_type='TRIP_STOP' and l.entity_id=s.id)
      and (select count(*) from document_links l where l.tenant_id=d.tenant_id and l.document_id=d.id)<=50
      and not exists(select 1 from document_links l where l.tenant_id=d.tenant_id and l.document_id=d.id and (
        l.entity_type<>'TRIP_STOP'
        or not exists(select 1 from trip_stops linked where linked.tenant_id=l.tenant_id and linked.id=l.entity_id)
        or not exists(select 1 from trip_stop_orders a join orders own on own.tenant_id=a.tenant_id and own.id=a.order_id
          where a.tenant_id=l.tenant_id and a.trip_stop_id=l.entity_id and own.customer_id=:customer)
        or exists(select 1 from trip_stop_orders a join orders foreign_order on foreign_order.tenant_id=a.tenant_id and foreign_order.id=a.order_id
          where a.tenant_id=l.tenant_id and a.trip_stop_id=l.entity_id and foreign_order.customer_id<>:customer)))
    """;
  private static final String DOCUMENT_SELECT="select distinct d.id,d.document_type,d.upload_status,d.content_type,d.size_bytes,d.uploaded_at,o.id order_id,o.order_number,s.id stop_id,ev.completed_at accepted_at,ev.evidence_mode ";
  private List<Document> documentsFor(UUID tenant,UUID customer,List<Delivery> page) {
    if(page.isEmpty())return List.of();var args=params(tenant,customer);var pairs=new ArrayList<String>();
    for(int i=0;i<page.size();i++){pairs.add("(o.id=:o"+i+" and s.id=:s"+i+")");args.put("o"+i,page.get(i).orderId());args.put("s"+i,page.get(i).stopId());}
    // Each receipt references at most two accepted files; bounded page membership bounds this query.
    return db.query(DOCUMENT_SELECT+DOCUMENTS+" and ("+String.join(" or ",pairs)+") order by d.uploaded_at desc,d.id,o.id,s.id",args,(r,n)->document(r,customer));
  }
  boolean containsDocument(UUID tenant,UUID customer,UUID document) {
    var args=params(tenant,customer);args.put("document",document);
    return !db.queryForList("select d.id "+DOCUMENTS+" and d.id=:document limit 1",args,UUID.class).isEmpty();
  }
  Page<Document> documents(AuthenticatedUserPrincipal actor,UUID tenant,UUID customer,Map<String,String> raw) {
    documentAuthority(actor,tenant,customer);
    var q=query(actor,tenant,customer,"documents",raw,Set.of("cursor","limit","documentType","orderId","stopId","documentId"),3);
    String filter="";
    for(String key:List.of("orderId","stopId","documentId"))if(raw.containsKey(key)){filter+=" and "+Map.of("orderId","o.id","stopId","s.id","documentId","d.id").get(key)+"=:"+key;q.args.put(key,uuid(raw.get(key)));}
    if(raw.containsKey("documentType")){if(!Set.of("BOL","POD","DELIVERY_PHOTO").contains(raw.get("documentType")))throw DocumentFailure.invalid();filter+=" and d.document_type=:type";q.args.put("type",raw.get("documentType"));}
    if(!q.after.isEmpty()) {
      q.args.put("afterDoc",q.after.get(0));q.args.put("afterOrder",q.after.get(1));q.args.put("afterStop",q.after.get(2));
      var anchors=db.queryForList("select d.uploaded_at "+DOCUMENTS+filter+" and d.id=:afterDoc and o.id=:afterOrder and s.id=:afterStop",q.args,Timestamp.class);
      if(anchors.isEmpty())throw DocumentFailure.invalid();q.args.put("afterTime",anchors.getFirst());
      filter+=" and (d.uploaded_at<:afterTime or (d.uploaded_at=:afterTime and (d.id,o.id,s.id)>(:afterDoc,:afterOrder,:afterStop)))";
    }
    var rows=db.query(DOCUMENT_SELECT+DOCUMENTS+filter+" order by d.uploaded_at desc,d.id,o.id,s.id limit :limit",q.args,(r,n)->document(r,customer));
    boolean more=rows.size()>q.limit;var page=List.copyOf(rows.subList(0,Math.min(rows.size(),q.limit)));
    return new Page<>(customer,page,more?ReadCursor.encode(q.context,page.getLast().id(),page.getLast().orderId(),page.getLast().stopId()):null);
  }
  private static Document document(ResultSet r,UUID customer)throws SQLException{return new Document(customer,r.getObject("id",UUID.class),r.getString("document_type"),r.getString("upload_status"),r.getString("content_type"),r.getLong("size_bytes"),time(r,"uploaded_at"),r.getObject("order_id",UUID.class),r.getString("order_number"),r.getObject("stop_id",UUID.class),time(r,"accepted_at"),r.getString("evidence_mode"));}
}
