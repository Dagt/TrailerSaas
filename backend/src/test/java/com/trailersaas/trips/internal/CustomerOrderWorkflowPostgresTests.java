package com.trailersaas.trips.internal;

import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerOrderWorkflowPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired TripService trips;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    OrderFixtures f;
    UUID tenant,customer,otherCustomer,user,dispatcher,membership,order,site;
    AuthenticatedUserPrincipal admin;
    @BeforeEach void seed() throws Exception {
        f=new OrderFixtures(flyway);tenant=f.db.tenant();f.db.execute("update tenants set status='ACTIVE' where id=?",tenant);
        customer=f.customer(tenant);otherCustomer=f.customer(tenant);user=f.db.user();f.db.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
        admin=new AuthenticatedUserPrincipal(user,"fixture-"+user,null,null);
        dispatcher=f.db.user();membership=f.db.membership(tenant,dispatcher,"ACTIVE",null);
        f.db.execute("insert into role_assignments values(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())",UUID.randomUUID(),tenant,membership,customer);
        when(verifier.verify("customer")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+dispatcher,null,null));
        order=f.order(tenant,customer,user);site=f.site(tenant,customer);f.stop(tenant,order,1,site,null,"{\"name\":\"Own destination\"}");
    }
    String base(){return "/api/v1/tenants/"+tenant;}
    HttpResponse<String> http(String method,String path,String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+base()+path)).timeout(Duration.ofSeconds(30))
            .header("Authorization","Bearer customer").header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String json(Object value) throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}
    void error(HttpResponse<String> r,int status,String code){assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);assertThat(JsonPath.<String>read(r.body(),"$.code")).isEqualTo(code);assertThat(r.body()).doesNotContain(otherCustomer.toString(),"SQL","constraint");}
    UUID plan(UUID... orders){return trips.create(admin,tenant,Map.of("orderIds",Arrays.stream(orders).map(UUID::toString).toList(),"stops",List.of(Map.of("sequenceNumber",1,"stopType","DELIVERY","siteSnapshot",Map.of("name","Own delivery"),"orderIds",List.of(order.toString()))))).id();}
    @Test void noBroadTripGrantAndAuthoritativeCustomerActions() throws Exception {
        error(http("GET","/trips",""),403,"CAPABILITY_DENIED");
        var r=http("GET","/orders/customer-context?customerId="+customer,"");assertThat(r.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(r.body(),"$.capabilities")).contains("ORDER_CREATE","ORDER_COMPLETE","TRIP_REROUTE").doesNotContain("TRIP_VIEW_ALL","TRIP_PLAN");
        error(http("GET","/orders/customer-context?customerId="+otherCustomer,""),403,"CUSTOMER_SCOPE_DENIED");
    }
    @Test void onlyOwnMappedStopsNoSharedRouteOrPrivateIdentity() throws Exception {
        var foreign=f.order(tenant,otherCustomer,user);
        var trip=trips.create(admin,tenant,Map.of("orderIds",List.of(order.toString(),foreign.toString()),"stops",List.of(
            Map.of("sequenceNumber",1,"stopType","DELIVERY","siteSnapshot",Map.of("name","Own delivery"),"orderIds",List.of(order.toString())),
            Map.of("sequenceNumber",2,"stopType","DELIVERY","siteSnapshot",Map.of("name","Secret foreign delivery"),"orderIds",List.of(foreign.toString()))))).id();
        var r=http("GET","/orders/"+order+"/progress","");assertThat(r.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<?>>read(r.body(),"$.items[0].stops")).hasSize(1);
        assertThat(r.body()).contains(trip.toString(),"Own delivery").doesNotContain(foreign.toString(),otherCustomer.toString(),"Secret foreign", "currentDriverId","currentTractorId","orderIds","actorUserId");
        assertThat(JsonPath.<Boolean>read(r.body(),"$.items[0].canSubstitute")).isFalse();
        error(http("GET","/orders/"+foreign+"/progress",""),404,"RESOURCE_NOT_FOUND");
    }
    @Test void progressPaginatesRealTripMappingsAndRejectsBadCursor() throws Exception {
        plan(order);plan(order);
        var first=http("GET","/orders/"+order+"/progress?limit=1","");assertThat(first.statusCode()).isEqualTo(200);
        String cursor=JsonPath.read(first.body(),"$.nextCursor");assertThat(cursor).isNotBlank();
        var last=http("GET","/orders/"+order+"/progress?limit=1&cursor="+cursor,"");assertThat(last.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Object>read(last.body(),"$.nextCursor")).isNull();
        assertThat(JsonPath.<String>read(first.body(),"$.items[0].id")).isNotEqualTo(JsonPath.<String>read(last.body(),"$.items[0].id"));
        error(http("GET","/orders/"+order+"/progress?cursor=bad",""),400,"VALIDATION_FAILED");
    }
    @ParameterizedTest @ValueSource(strings={"tenant","membership","role"})
    void accessRevocationBlocksReadsAndMutations(String change) throws Exception {
        if(change.equals("tenant")) f.db.execute("update tenants set status='DISABLED' where id=?",tenant);
        if(change.equals("membership")) f.db.execute("update tenant_memberships set status='SUSPENDED' where id=?",membership);
        if(change.equals("role")) f.db.execute("delete from role_assignments where membership_id=?",membership);
        assertThat(http("GET","/orders?customerId="+customer,"").statusCode()).isEqualTo(403);
        assertThat(http("PATCH","/orders/"+order,"{\"expectedVersion\":0,\"poNumber\":\"No\"}").statusCode()).isEqualTo(403);
        assertThat(f.db.value("select version from orders where id=?",order)).isEqualTo(0L);
    }
    @Test void requestEditIsAtomicAndRetainsSpecificPreviousHistory() throws Exception {
        var body=json(Map.of("expectedVersion",0,"cargoLines",List.of(Map.of("description","First","quantity",2),Map.of("description","Second","palletCount",3)),"requestedStops",List.of(Map.of("sequenceNumber",1,"siteId",site.toString(),"stopType","PICKUP"),Map.of("sequenceNumber",2,"siteSnapshot",Map.of("name","New ad hoc"),"stopType","DELIVERY"))));
        var r=http("PATCH","/orders/"+order,body);assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
        assertThat(JsonPath.<List<?>>read(r.body(),"$.cargoLines")).hasSize(2);assertThat(JsonPath.<List<?>>read(r.body(),"$.requestedStops")).hasSize(2);
        assertThat(f.db.value("select count(*) from order_request_revisions where order_id=?",order)).isEqualTo(1L);
        assertThat(f.db.value("select previous_request::text from order_request_revisions where order_id=?",order).toString()).contains("Own destination");
        error(http("PATCH","/orders/"+order,body),409,"STALE_VERSION");
    }
    @Test void plannedRequestsCannotRewriteDestinationsOrCargo() throws Exception {
        plan(order);var before=f.db.value("select jsonb_agg(to_jsonb(s))::text from order_stops s where order_id=?",order);
        error(http("PATCH","/orders/"+order,"{\"expectedVersion\":1,\"cargoLines\":[]}"),409,"INVALID_STATE_TRANSITION");
        assertThat(f.db.value("select jsonb_agg(to_jsonb(s))::text from order_stops s where order_id=?",order)).isEqualTo(before);
        assertThat(f.db.value("select count(*) from order_request_revisions where order_id=?",order)).isEqualTo(0L);
    }
    @Test void invalidDestinationRollsBackReferenceChildrenAndHistory() throws Exception {
        var foreignSite=f.site(tenant,otherCustomer);
        error(http("PATCH","/orders/"+order,json(Map.of("expectedVersion",0,"poNumber","Must roll back","requestedStops",List.of(Map.of("sequenceNumber",1,"siteId",foreignSite.toString(),"stopType","DELIVERY"))))),409,"RESOURCE_CONFLICT");
        assertThat(f.db.value("select version from orders where id=?",order)).isEqualTo(0L);assertThat(f.db.value("select po_number from orders where id=?",order)).isNull();
        assertThat(f.db.value("select count(*) from order_request_revisions where order_id=?",order)).isEqualTo(0L);
    }
    @Test void explicitCustomerCatalogFilterDoesNotReturnOtherAssignedCustomers() throws Exception {
        var foreignSite=f.site(tenant,otherCustomer);
        f.db.execute("insert into role_assignments values(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())",UUID.randomUUID(),tenant,membership,otherCustomer);
        var r=http("GET","/sites?customerId="+customer,"");assertThat(r.statusCode()).isEqualTo(200);assertThat(r.body()).contains(site.toString()).doesNotContain(foreignSite.toString());
        error(http("GET","/sites/"+foreignSite+"/locations?customerId="+customer,""),404,"RESOURCE_NOT_FOUND");
    }
    @Test void twoRequestEditsSameVersionHaveExactlyOneWinner() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<Integer>>();
            for(var label:List.of("One","Two")) jobs.add(pool.submit(()->{gate.await();return http("PATCH","/orders/"+order,json(Map.of("expectedVersion",0,"cargoLines",List.of(Map.of("description",label))))).statusCode();}));
            gate.countDown();assertThat(List.of(jobs.get(0).get(30,TimeUnit.SECONDS),jobs.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(f.db.value("select count(*) from order_request_revisions where order_id=?",order)).isEqualTo(1L);
        }
    }
}
