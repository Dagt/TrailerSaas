package com.trailersaas.documents.internal;

import static org.assertj.core.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.TrailerFixtures;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerOperationalReadsPostgresTests extends DocumentHttpPostgresFixture {
  UUID order;
  @BeforeEach void context(){try{order=(UUID)db.value("select order_id from trip_stop_orders where tenant_id=? and trip_stop_id=?",tenant,stop);}catch(SQLException e){throw new IllegalStateException(e);}}
  String portal(){return "/api/v1/tenants/"+tenant+"/customers/"+customerA;}
  HttpResponse<String> get(String path)throws Exception{return http("customer","GET",portal()+path,null,null);}
  String document(UUID id){return base()+"/"+id+"?customerId="+customerA;}
  String download(UUID id){return base()+"/"+id+"/download-url?customerId="+customerA;}
  List<Map<String,Object>> items(HttpResponse<String> r){assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);return JsonPath.read(r.body(),"$.items");}
  UUID acceptedFile()throws Exception{var id=uploaded();assertThat(link(id,stop).statusCode()).isEqualTo(200);receipt(id);return id;}
  void receipt(UUID file)throws Exception{
    db.execute("update trip_stops set status='COMPLETED',arrived_at=now(),service_started_at=now(),completed_at=now() where id=?",stop);
    db.execute("insert into delivery_evidence(id,tenant_id,trip_stop_id,evidence_mode,completed_by_user_id,completed_at,bol_document_id) values(?,?,?,'SIGNED_BOL',?,now(),?)",UUID.randomUUID(),tenant,stop,user,file);
  }
  UUID anotherStop(UUID customer,boolean anotherTrip)throws Exception{
    var original=stop;var parent=trip;
    if(anotherTrip){parent=UUID.randomUUID();db.execute("insert into trips(id,tenant_id,status,current_driver_id,created_at,updated_at) values(?,?,'ASSIGNED',?,now(),now())",parent,tenant,driver);}
    var next=UUID.randomUUID();
    db.execute("insert into trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at) values(?,?,?,?, 'DELIVERY','PENDING','{\"name\":\"Authorized destination\"}',now(),now())",next,tenant,parent,anotherTrip?1:10);
    stop=next;try{mapOrder(customer);}finally{stop=original;}return next;
  }
  @Test void deliveryUsesActualOrderStopAndNoInternalEquipment()throws Exception{
    var r=get("/deliveries");assertThat(items(r)).hasSize(1);var row=items(r).getFirst();
    assertThat(row).containsEntry("customerId",customerA.toString()).containsEntry("orderId",order.toString()).containsEntry("stopId",stop.toString()).containsEntry("status","PENDING");
    assertThat(r.body()).doesNotContain("driverId","tractorId","actorUserId","instructions","metadata","uploaded_by",customerB.toString());
    var detail=get("/deliveries/"+order+"/"+stop);assertThat(detail.statusCode()).isEqualTo(200);assertThat(JsonPath.<String>read(detail.body(),"$.stopId")).isEqualTo(stop.toString());
  }
  @Test void multipleTripsAndStopsDoNotBecomeOneDeliveryAndNoOtherCustomersAppear()throws Exception{
    var second=anotherStop(customerA,true);var foreign=anotherStop(customerB,false);
    // Map the original Order to another actual Trip; one Order can have multiple deliveries.
    db.execute("insert into trip_stop_orders(id,tenant_id,trip_stop_id,order_id) values(?,?,?,?)",UUID.randomUUID(),tenant,second,order);
    var rows=items(get("/deliveries"));assertThat(rows).hasSize(3);
    assertThat(rows.stream().filter(r->r.get("orderId").equals(order.toString())).toList()).hasSize(2);
    assertThat(rows.toString()).doesNotContain(foreign.toString(),customerB.toString());
  }
  @Test void boundedKeysetKeepsEveryDistinctOrderStop()throws Exception{
    anotherStop(customerA,true);var first=get("/deliveries?limit=1");var cursor=JsonPath.<String>read(first.body(),"$.nextCursor");
    var second=get("/deliveries?limit=1&cursor="+cursor);assertThat(items(first)).hasSize(1);assertThat(items(second)).hasSize(1);
    assertThat(items(first).getFirst().get("stopId")).isNotEqualTo(items(second).getFirst().get("stopId"));assertThat(JsonPath.<Object>read(second.body(),"$.nextCursor")).isNull();
    assertProblem(get("/deliveries?limit=1&status=PENDING&cursor="+cursor),400,"VALIDATION_FAILED");
  }
  @Test void publishedAcceptedFilesOnlyNotEveryUploadOrBolReference()throws Exception{
    var accepted=acceptedFile();var extra=uploaded();assertThat(link(extra,stop).statusCode()).isEqualTo(200);
    db.execute("update orders set bol_reference='A textual BOL is not a file' where id=?",order);
    var page=get("/documents");assertThat(items(page)).hasSize(1);assertThat(items(page).getFirst()).containsEntry("id",accepted.toString()).containsEntry("documentType","BOL");
    assertThat(page.body()).doesNotContain(extra.toString(),"objectKey","checksumSha256","downloadUrl","uploader",user.toString());
    var delivery=get("/deliveries/"+order+"/"+stop);assertThat(JsonPath.<List<?>>read(delivery.body(),"$.documents")).hasSize(1);
    assertProblem(http("customer","GET",document(extra),null,null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void realAuthorizedDownloadContainsPublishedBytesAndRechecksAuthority()throws Exception{
    var id=acceptedFile();assertThat(http("customer","GET",document(id),null,null).statusCode()).isEqualTo(200);
    var signed=http("customer","POST",download(id),"{}",null);assertThat(signed.statusCode()).withFailMessage(signed.body()).isEqualTo(200);
    var url=JsonPath.<String>read(signed.body(),"$.downloadUrl");var bytesResponse=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    assertThat(bytesResponse.statusCode()).isEqualTo(200);assertThat(bytesResponse.body()).containsExactly(bytes);
    db.execute("delete from role_assignments where membership_id in(select id from tenant_memberships where tenant_id=? and user_id=?)",tenant,customerUser);
    assertProblem(http("customer","POST",download(id),"{}",null),403,"CAPABILITY_DENIED");
  }
  @Test void historicalAcceptedReceiptNeverGuessesAFileFromCurrentLinks()throws Exception{
    var unproven=uploaded();link(unproven,stop);receipt(null);
    assertThat(items(get("/documents"))).isEmpty();var delivery=get("/deliveries/"+order+"/"+stop);
    assertThat(JsonPath.<String>read(delivery.body(),"$.evidenceMode")).isEqualTo("SIGNED_BOL");
    assertThat(JsonPath.<Boolean>read(delivery.body(),"$.fileAssociationRecorded")).isFalse();assertThat(JsonPath.<List<?>>read(delivery.body(),"$.documents")).isEmpty();
    assertProblem(http("customer","POST",download(unproven),"{}",null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void pendingAndUnassociatedFilesCannotBeDiscovered()throws Exception{
    var pending=pending();db.execute("insert into document_links values(?, ?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,pending,stop);receipt(pending);
    assertThat(items(get("/documents"))).isEmpty();assertProblem(http("customer","GET",document(pending),null,null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void sharedStopIsFailClosedEvenForTwoCustomerAssignments()throws Exception{
    var id=acceptedFile();mapOrder(customerB);var cm=(UUID)db.value("select id from tenant_memberships where tenant_id=? and user_id=?",tenant,customerUser);
    new TrailerFixtures(flyway).role(tenant,cm,"CUSTOMER_DISPATCHER",customerB);
    assertThat(items(get("/documents"))).isEmpty();assertProblem(http("customer","GET",document(id),null,null),404,"RESOURCE_NOT_FOUND");
    var own=get("/deliveries/"+order+"/"+stop);assertThat(own.statusCode()).isEqualTo(200);assertThat(JsonPath.<List<?>>read(own.body(),"$.documents")).isEmpty();
    assertThat(JsonPath.<Object>read(own.body(),"$.evidenceMode")).isNull();assertThat(own.body()).doesNotContain(customerB.toString());
  }
  @Test void documentWithOneAuthorizedAndOneForeignLinkNeverAppears()throws Exception{
    var id=acceptedFile();var hidden=anotherStop(customerB,true);db.execute("insert into document_links values(?,?,?,'TRIP_STOP',?,now())",UUID.randomUUID(),tenant,id,hidden);
    assertThat(items(get("/documents"))).isEmpty();assertProblem(http("customer","POST",download(id),"{}",null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void internalTripAttachmentCannotBeAuthorizedByADeliveryLink()throws Exception{
    var id=acceptedFile();db.execute("insert into document_links values(?,?,?,'TRIP',?,now())",UUID.randomUUID(),tenant,id,trip);
    assertThat(items(get("/documents"))).isEmpty();assertProblem(http("customer","GET",document(id),null,null),404,"RESOURCE_NOT_FOUND");
  }
  @Test void adminRoleCannotWidenAnExplicitCustomerDownload()throws Exception{
    var id=acceptedFile();mapOrder(customerB);
    assertProblem(http("admin","POST",download(id),"{}",null),404,"RESOURCE_NOT_FOUND");
    assertThat(http("admin","POST",base()+"/"+id+"/download-url","{}",null).statusCode()).isEqualTo(200);
  }
  @Test void foreignAndMissingDeliveryIdsHaveTheSameSafeResponse()throws Exception{
    var foreign=anotherStop(customerB,true);var foreignOrder=(UUID)db.value("select order_id from trip_stop_orders where trip_stop_id=?",foreign);
    var r=get("/deliveries/"+foreignOrder+"/"+foreign);assertProblem(r,404,"RESOURCE_NOT_FOUND");
    assertThat(r.body()).doesNotContain("orderNumber","destination","driverId",customerB.toString());
    assertProblem(get("/deliveries/"+UUID.randomUUID()+"/"+UUID.randomUUID()),404,"RESOURCE_NOT_FOUND");
  }
  @ParameterizedTest @ValueSource(strings={"tenant","membership","role"})
  void revocationStopsAllCustomerProjectionsAndDownloads(String kind)throws Exception{
    var id=acceptedFile();
    switch(kind){case "tenant"->db.execute("update tenants set status='DISABLED' where id=?",tenant);case "membership"->db.execute("update tenant_memberships set status='SUSPENDED' where tenant_id=? and user_id=?",tenant,customerUser);default->db.execute("delete from role_assignments where membership_id in(select id from tenant_memberships where tenant_id=? and user_id=?)",tenant,customerUser);}
    for(var suffix:List.of("/deliveries","/documents"))assertThat(get(suffix).statusCode()).isEqualTo(403);
    assertThat(http("customer","POST",download(id),"{}",null).statusCode()).isEqualTo(403);
    assertThat(http("customer","GET",base()+"/"+id,null,null).statusCode()).isEqualTo(403);
  }
  @ParameterizedTest @ValueSource(strings={"limit=0","limit=51","unknown=true","cursor=bad","status=UNKNOWN","active=maybe","orderId=bad"})
  void deliveryQueriesAreClosedAndBounded(String query)throws Exception{assertProblem(get("/deliveries?"+query),400,"VALIDATION_FAILED");}
  @ParameterizedTest @ValueSource(strings={"limit=0","limit=51","cursor=bad","documentType=DAMAGE_PHOTO","orderId=bad","uploadStatus=PENDING"})
  void documentQueriesAreClosedAndBounded(String query)throws Exception{assertProblem(get("/documents?"+query),400,"VALIDATION_FAILED");}
  @Test void documentCursorCannotCrossSelectedCustomerEvenForAMultiCustomerUser()throws Exception{
    acceptedFile();var otherOrder=mapOrder(customerA);var first=get("/documents?limit=1");var cursor=JsonPath.<String>read(first.body(),"$.nextCursor");assertThat(cursor).isNotBlank();
    var second=get("/documents?limit=1&cursor="+cursor);assertThat(items(second)).hasSize(1);assertThat(items(first).getFirst().get("orderId")).isNotEqualTo(items(second).getFirst().get("orderId"));
    var cm=(UUID)db.value("select id from tenant_memberships where tenant_id=? and user_id=?",tenant,customerUser);new TrailerFixtures(flyway).role(tenant,cm,"CUSTOMER_DISPATCHER",customerB);
    assertProblem(http("customer","GET",portal().replace(customerA.toString(),customerB.toString())+"/documents?limit=1&cursor="+cursor,null,null),400,"VALIDATION_FAILED");
    assertThat(items(get("/documents?orderId="+otherOrder))).hasSize(1);
  }
  @Test void readOnlyRequestsCannotMutatePhysicalOrBusinessState()throws Exception{
    var id=acceptedFile();var before=db.value("select to_jsonb(t)::text from trips t where id=?",trip);
    get("/documents");get("/deliveries");http("customer","GET",document(id),null,null);
    assertThat(db.value("select to_jsonb(t)::text from trips t where id=?",trip)).isEqualTo(before);
    assertThat(db.value("select count(*) from trailer_movement_events where tenant_id=?",tenant)).isEqualTo(0L);
    assertThat(db.value("select count(*) from order_fulfillment_events where tenant_id=?",tenant)).isEqualTo(0L);
  }
  @Test void currentCustomerDoesNotGrantBroadTripOrInternalCatalogAccess()throws Exception{
    assertThat(http("customer","GET","/api/v1/tenants/"+tenant+"/trips",null,null).statusCode()).isEqualTo(403);
    assertThat(http(null,"GET",portal()+"/deliveries",null,null).statusCode()).isEqualTo(401);
    assertThat(http("customer","GET","/api/v1/tenants/"+other+"/customers/"+customerA+"/deliveries",null,null).statusCode()).isEqualTo(403);
  }
  @Test void acceptedFileColumnsEnforceTenantAndModeWithoutRuntimeRewrite()throws Exception{
    var foreign=UUID.randomUUID();db.execute("insert into documents values(?,?,?,'BOL','image/jpeg',3,?,'UPLOADED',?,'USER_ACTION',now(),now())",foreign,other,"private/"+foreign,"0".repeat(64),user);
    assertThatThrownBy(()->db.execute("insert into delivery_evidence(id,tenant_id,trip_stop_id,evidence_mode,completed_by_user_id,completed_at,bol_document_id) values(?,?,?,'SIGNED_BOL',?,now(),?)",UUID.randomUUID(),tenant,stop,user,foreign)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23503"));
    var own=uploaded();
    assertThatThrownBy(()->db.execute("insert into delivery_evidence(id,tenant_id,trip_stop_id,evidence_mode,comment,completed_by_user_id,completed_at,bol_document_id) values(?,?,?,'NO_RECEIVER_AVAILABLE','No receiver',?,now(),?)",UUID.randomUUID(),tenant,stop,user,own)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
  }
}
