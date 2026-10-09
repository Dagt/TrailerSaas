package com.trailersaas.orders.internal;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RecordApplicationEvents
class OrderMutationPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DataSource runtime;
    @Autowired OrderLifecyclePort lifecycle;
    @Autowired OrderMutationService mutations;
    @Autowired ManifestService manifests;
    @Autowired TenantTransactionExecutor transactions;
    @Autowired ApplicationEvents events;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    OrderFixtures f;
    UUID tenant, otherTenant, customer, foreignCustomer, user, membership, order;
    AuthenticatedUserPrincipal admin;
    @BeforeEach void seed() throws Exception {
        f = new OrderFixtures(flyway); tenant = f.db.tenant(); otherTenant = f.db.tenant(); customer = f.customer(tenant);
        foreignCustomer = f.customer(tenant); user = actor("admin", "TENANT_ADMIN");
        membership = (UUID) f.db.value("SELECT id FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, user);
        admin = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
        actor("ops", "TRANSPORT_OPERATIONS"); actor("yard", "YARD_SPOTTER"); actor("driver", "DRIVER");
        var dispatcher = actor("dispatcher", null);
        var m = f.db.value("SELECT id FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, dispatcher);
        f.db.execute("INSERT INTO role_assignments VALUES(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())", UUID.randomUUID(), tenant, m, customer);
        order = f.order(tenant, customer, user);
        f.stop(tenant, order, 1, null, null, "{\"name\":\"Frozen destination\"}");
        f.db.execute("INSERT INTO order_cargo_lines(id,tenant_id,order_id,description,created_at) VALUES(?,?,?,'Retain cargo',now())", UUID.randomUUID(), tenant, order);
    }
    UUID actor(String token, String role) throws Exception {
        var u = f.db.user(); f.db.membership(tenant, u, "ACTIVE", role);
        when(verifier.verify(token)).thenReturn(new VerifiedFirebaseIdentity("fixture-" + u, null, null)); return u;
    }
    HttpResponse<String> http(String method, UUID id, String suffix, String token, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/tenants/" + tenant + "/orders/" + id + suffix))
                .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json").header("X-Request-Id", "order-mutation-regression");
        if (token != null) { b.header("Authorization", "Bearer " + token); }
        return HttpClient.newHttpClient().send(b.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> patch(String token, String body) throws Exception { return http("PATCH", order, "", token, body); }
    HttpResponse<String> cancel(String body) throws Exception { return http("POST", order, "/cancellations", "admin", body); }
    void problem(HttpResponse<String> r, int status, String code) {
        assertThat(r.statusCode()).isEqualTo(status); assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
        assertThat(r.headers().firstValue("X-Request-Id")).contains("order-mutation-regression");
        assertThat(JsonPath.<String>read(r.body(), "$.traceId")).isNotBlank();
        assertThat(r.body()).doesNotContain("SQL", "constraint", "firebase", customer.toString(), foreignCustomer.toString());
    }
    String children() throws Exception {
        return f.db.value("SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM order_stops s WHERE order_id=?", order)
                + ":" + f.db.value("SELECT jsonb_agg(to_jsonb(c) ORDER BY id)::text FROM order_cargo_lines c WHERE order_id=?", order);
    }
    long version() throws Exception { return (Long) f.db.value("SELECT version FROM orders WHERE id=?", order); }
    void state(String status) throws Exception {
        f.db.execute("UPDATE orders SET status=?,delivered_at=CASE WHEN ? IN ('DELIVERED','COMPLETED') THEN now() ELSE NULL END,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END,cancelled_at=CASE WHEN ?='CANCELLED' THEN now() ELSE NULL END WHERE id=?", status,status,status,status,order);
    }
    @ParameterizedTest @ValueSource(strings = {"admin", "ops", "dispatcher"})
    void scopedPatchSupportsNullOmissionNoOpAndSingleVersionAdvance(String token) throws Exception {
        var history = children();
        var r = patch(token, "{\"expectedVersion\":0,\"poNumber\":\"PO\",\"bolReference\":\"BOL\"}");
        assertThat(r.statusCode()).isEqualTo(200); assertThat(version()).isEqualTo(1);
        r = patch(token, "{\"expectedVersion\":1,\"poNumber\":null}"); assertThat(r.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Object>read(r.body(),"$.poNumber")).isNull(); assertThat(JsonPath.<String>read(r.body(),"$.bolReference")).isEqualTo("BOL");
        assertThat(patch(token, "{\"expectedVersion\":2}").statusCode()).isEqualTo(200); assertThat(version()).isEqualTo(2);
        assertThat(children()).isEqualTo(history); problem(patch(token, "{\"expectedVersion\":0}"),409,"STALE_VERSION");
    }
    @ParameterizedTest @EnumSource(OrderStatus.class)
    void patchOnlyOpenOrPlanned(OrderStatus status) throws Exception {
        state(status.name()); var r=patch("admin", "{\"expectedVersion\":0,\"shipmentNumber\":\"NEW\"}");
        if (status==OrderStatus.OPEN || status==OrderStatus.PLANNED) { assertThat(r.statusCode()).isEqualTo(200); }
        else { problem(r,409,"INVALID_STATE_TRANSITION"); assertThat(version()).isZero(); }
    }
    @ParameterizedTest @ValueSource(strings={"orderNumber","customerId","cargoLines","requestedStops","plannedTrailerId","status","version","deliveredAt"})
    void patchRejectsEveryFieldOutsideReferences(String field) throws Exception {
        problem(patch("admin","{\"expectedVersion\":0,\""+field+"\":null}"),400,"VALIDATION_FAILED"); assertThat(version()).isZero();
    }
    @Test void securityIsFreshScopedAndIndependentOfFirebaseClaims() throws Exception {
        problem(patch(null,"{\"expectedVersion\":0}"),401,"AUTHENTICATION_REQUIRED");
        for(var token:List.of("yard","driver")) { problem(patch(token,"{\"expectedVersion\":0}"),403,"CAPABILITY_DENIED"); }
        var invisible=f.order(tenant,foreignCustomer,user); problem(http("PATCH",invisible,"","dispatcher","{\"expectedVersion\":0}"),404,"RESOURCE_NOT_FOUND");
        var foreign=f.order(otherTenant,f.customer(otherTenant),user); problem(http("PATCH",foreign,"","admin","{\"expectedVersion\":0}"),404,"RESOURCE_NOT_FOUND");
        f.db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?",membership);
        problem(patch("admin","{\"expectedVersion\":0}"),403,"TENANT_ACCESS_DENIED");
        f.db.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?",membership); f.db.execute("DELETE FROM role_assignments WHERE membership_id=?",membership);
        problem(patch("admin","{\"expectedVersion\":0}"),403,"CAPABILITY_DENIED");
    }
    @Test void sameVersionConcurrentHttpPatchesHaveExactlyOneWinner() throws Exception {
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new ArrayList<Future<HttpResponse<String>>>();
            for(var po:List.of("ONE","TWO")) { futures.add(pool.submit(()->{ waitFor(gate); return patch("admin","{\"expectedVersion\":0,\"poNumber\":\""+po+"\"}"); })); }
            gate.countDown(); var results=new ArrayList<HttpResponse<String>>(); for(var future:futures) { results.add(future.get(30,TimeUnit.SECONDS)); }
            assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200,409);
            problem(results.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"STALE_VERSION"); assertThat(version()).isEqualTo(1);
        }
    }
    @ParameterizedTest @ValueSource(strings={"OPEN","PLANNED"})
    void directCancellationReleasesOnlyOpenAllocationsAtomicallyAndRetainsClosedHistory(String initial) throws Exception {
        state(initial); var history=children(); var trailer=new TrailerFixtures(flyway).trailer(tenant,"C-"+UUID.randomUUID(),customer);
        var physical=f.db.value("SELECT to_jsonb(t)::text FROM trailers t WHERE id=?",trailer);
        var open=manifests.create(admin,tenant,trailer); var closed=manifests.create(admin,tenant,trailer);
        // Fixture can preserve accepted PLANNED history while #32 add only accepts OPEN.
        allocation(open.id(),order); allocation(closed.id(),order);
        var second=f.order(tenant,customer,user); allocation(open.id(),second); manifests.close(admin,tenant,closed.id(),0);
        var closedBefore=f.db.value("SELECT to_jsonb(a)::text FROM manifest_orders a WHERE manifest_id=?",closed.id());
        var r=cancel("{\"expectedVersion\":0,\"reason\":\"Cancelled before execution\"}"); assertThat(r.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(r.body(),"$.status")).isEqualTo("CANCELLED"); assertThat(JsonPath.<String>read(r.body(),"$.cancelledAt")).isNotBlank();
        assertThat(version()).isEqualTo(1); assertThat(children()).isEqualTo(history);
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE manifest_id=? AND order_id=?",open.id(),order)).isEqualTo(0L);
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE manifest_id=? AND order_id=?",open.id(),second)).isEqualTo(1L);
        assertThat(f.db.value("SELECT status FROM load_manifests WHERE id=?",open.id())).isEqualTo("OPEN");
        assertThat(f.db.value("SELECT version FROM load_manifests WHERE id=?",open.id())).isEqualTo(1L);
        assertThat(f.db.value("SELECT to_jsonb(a)::text FROM manifest_orders a WHERE manifest_id=?",closed.id())).isEqualTo(closedBefore);
        assertThat(f.db.value("SELECT to_jsonb(t)::text FROM trailers t WHERE id=?",trailer)).isEqualTo(physical);
    }
    void allocation(UUID manifest,UUID id) throws Exception { f.db.execute("INSERT INTO manifest_orders(id,tenant_id,manifest_id,order_id,created_at) VALUES(?,?,?,?,now())",UUID.randomUUID(),tenant,manifest,id); }
    @ParameterizedTest @ValueSource(strings={"IN_PROGRESS","PARTIALLY_DELIVERED"})
    void activeCancellationRequiresResolutionRetainsPayloadAndFinishesOnlyViaHook(String initial) throws Exception {
        state(initial); var history=children(); var site=f.site(tenant,customer);
        problem(cancel("{\"expectedVersion\":0,\"reason\":\"Return cargo\"}"),400,"VALIDATION_FAILED");
        problem(cancel("{\"expectedVersion\":0,\"reason\":\"Return cargo\",\"resolution\":{\"type\":\"RETURN\"}}"),400,"VALIDATION_FAILED");
        var result=mutations.cancel(admin,tenant,order,Map.of("expectedVersion",0,"reason","Return cargo","resolution",Map.of("type","RETURN","destinationSiteId",site.toString(),"instructions","Return remaining cargo to receiving")));
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLING); assertThat(result.cancelledAt()).isNull(); assertThat(result.version()).isEqualTo(1);
        var event=events.stream(OrderLifecycleEvent.class).filter(e->e.action()==OrderLifecycleEvent.Action.ORDER_CANCELLATION_REQUESTED).findFirst().orElseThrow();
        assertThat(event.tenantId()).isEqualTo(tenant); assertThat(event.orderId()).isEqualTo(order); assertThat(event.customerId()).isEqualTo(customer);
        assertThat(event.actor().userId()).isEqualTo(user); assertThat(event.actor().membershipId()).isEqualTo(membership); assertThat(event.actor().actorType()).isEqualTo("HUMAN_USER");
        assertThat(event.reason()).isEqualTo("Return cargo"); assertThat(event.previousState()).isEqualTo(initial); assertThat(event.newState()).isEqualTo("CANCELLING");
        assertThat(event.resolution()).isEqualTo(new OrderLifecycleEvent.Resolution("RETURN",site,"Return remaining cargo to receiving")); assertThat(event.occurredAt()).isEqualTo(result.updatedAt());
        var ended=transactions.execute(tenant,()->lifecycle.finishCancellation(tenant,order,1)); assertThat(ended.status()).isEqualTo("CANCELLED"); assertThat(ended.version()).isEqualTo(2);
        assertThat(f.db.value("SELECT cancelled_at FROM orders WHERE id=?",order)).isNotNull(); assertThat(children()).isEqualTo(history);
    }
    @ParameterizedTest @ValueSource(strings={"IN_PROGRESS","PARTIALLY_DELIVERED"})
    void activeResolutionRejectsForeignOrUnassociatedSiteWithoutChangingState(String initial) throws Exception {
        state(initial); var site=f.site(tenant,foreignCustomer);
        problem(cancel("{\"expectedVersion\":0,\"reason\":\"Reroute\",\"resolution\":{\"type\":\"RETURN\",\"destinationSiteId\":\""+site+"\",\"instructions\":\"Return\"}}"),409,"RESOURCE_CONFLICT");
        assertThat(version()).isZero(); assertThat(f.db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo(initial);
    }
    @Test void lifecycleHooksAdvanceExactlyOnceWithServerMilestonesAndNoPlanningSideEffect() throws Exception {
        var history=children(); var previous=f.db.value("SELECT updated_at FROM orders WHERE id=?",order);
        var steps=List.<java.util.function.LongFunction<OrderLifecyclePort.Revision>>of(
                v->lifecycle.plan(tenant,order,v),v->lifecycle.startFulfillment(tenant,order,v),v->lifecycle.recordPartialDelivery(tenant,order,v),
                v->lifecycle.resumeFulfillment(tenant,order,v),v->lifecycle.deliver(tenant,order,v));
        long v=0;
        for(var step:steps) {
            final long current=v++; var result=transactions.execute(tenant,()->step.apply(current)); assertThat(result.version()).isEqualTo(v);
            assertThat(result.updatedAt()).isAfter(((java.sql.Timestamp)previous).toInstant()); previous=java.sql.Timestamp.from(result.updatedAt());
            assertThat(f.db.value("SELECT completed_at FROM orders WHERE id=?",order)).isNull(); assertThat(f.db.value("SELECT cancelled_at FROM orders WHERE id=?",order)).isNull();
            if(v<5) { assertThat(f.db.value("SELECT delivered_at FROM orders WHERE id=?",order)).isNull(); }
        }
        var delivered=f.db.value("SELECT delivered_at FROM orders WHERE id=?",order);
        var completed=http("POST",order,"/completion","admin","{\"expectedVersion\":5}"); assertThat(completed.statusCode()).isEqualTo(200);
        assertThat(f.db.value("SELECT delivered_at FROM orders WHERE id=?",order)).isEqualTo(delivered); assertThat(version()).isEqualTo(6);
        assertThat(f.db.value("SELECT completed_at FROM orders WHERE id=?",order)).isNotNull(); assertThat(children()).isEqualTo(history);
        problem(http("POST",order,"/completion","admin","{\"expectedVersion\":6}"),409,"INVALID_STATE_TRANSITION");
        assertThatThrownBy(()->transactions.execute(tenant,()->lifecycle.plan(tenant,order,6))).isInstanceOf(OrderFailure.class);
    }
    @ParameterizedTest @EnumSource(value=OrderStatus.class,names={"DELIVERED"},mode=EnumSource.Mode.EXCLUDE)
    void completionRejectsEveryOtherState(OrderStatus status) throws Exception { state(status.name()); problem(http("POST",order,"/completion","admin","{\"expectedVersion\":0}"),409,"INVALID_STATE_TRANSITION"); }
    @ParameterizedTest @CsvSource({"OPEN,plan,PLANNED", "PLANNED,start,IN_PROGRESS", "IN_PROGRESS,partial,PARTIALLY_DELIVERED",
            "PARTIALLY_DELIVERED,resume,IN_PROGRESS", "IN_PROGRESS,deliver,DELIVERED", "PARTIALLY_DELIVERED,deliver,DELIVERED",
            "CANCELLING,finish,CANCELLED"})
    void everyDomainHookPreservesChildrenAndAdvancesVersionAndTime(String initial,String command,String expected) throws Exception {
        state(initial); var history=children(); var before=(java.sql.Timestamp)f.db.value("SELECT updated_at FROM orders WHERE id=?",order);
        var result=transactions.execute(tenant,()->switch(command) {
            case "plan" -> lifecycle.plan(tenant,order,0);
            case "start" -> lifecycle.startFulfillment(tenant,order,0);
            case "partial" -> lifecycle.recordPartialDelivery(tenant,order,0);
            case "resume" -> lifecycle.resumeFulfillment(tenant,order,0);
            case "deliver" -> lifecycle.deliver(tenant,order,0);
            default -> lifecycle.finishCancellation(tenant,order,0);
        });
        assertThat(result.status()).isEqualTo(expected);assertThat(result.version()).isEqualTo(1);assertThat(result.updatedAt()).isAfter(before.toInstant());assertThat(children()).isEqualTo(history);
        assertThat(f.db.value("SELECT completed_at FROM orders WHERE id=?",order)).isNull();
        assertThat(f.db.value("SELECT delivered_at FROM orders WHERE id=?",order)).isEqualTo(expected.equals("DELIVERED")?java.sql.Timestamp.from(result.updatedAt()):null);
        assertThat(f.db.value("SELECT cancelled_at FROM orders WHERE id=?",order)).isEqualTo(expected.equals("CANCELLED")?java.sql.Timestamp.from(result.updatedAt()):null);
    }
    @ParameterizedTest @ValueSource(strings={"/cancellations","/completion"})
    void commandBodiesAreStrictAndVersionsRequired(String suffix) throws Exception {
        for(var body:List.of("{}","{\"expectedVersion\":-1}","{\"expectedVersion\":0.5}","{\"expectedVersion\":0,\"reason\":\"R\",\"status\":\"COMPLETED\"}")) {
            problem(http("POST",order,suffix,"admin",body),400,"VALIDATION_FAILED");
        }
        assertThat(version()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"DELIVERED","COMPLETED","CANCELLING","CANCELLED"})
    void cancellationRejectsNonCancellableStates(String initial) throws Exception {
        state(initial);problem(cancel("{\"expectedVersion\":0,\"reason\":\"Cancel\"}"),409,"INVALID_STATE_TRANSITION");assertThat(version()).isZero();
    }
    @Test void staleCancellationAndCompletionAreDistinctFromInvalidState() throws Exception {
        assertThat(patch("admin","{\"expectedVersion\":0,\"poNumber\":\"One\"}").statusCode()).isEqualTo(200);
        problem(cancel("{\"expectedVersion\":0,\"reason\":\"Cancel\"}"),409,"STALE_VERSION");
        state("DELIVERED");problem(http("POST",order,"/completion","admin","{\"expectedVersion\":0}"),409,"STALE_VERSION");
    }
    @Test void orderUpdateRlsStillRequiresExactTenantAndRejectsBootstrapOrCrossTenantMoves() throws Exception {
        try(var c=runtime.getConnection()) {
            c.setAutoCommit(false);assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            assertThat(f.db.execute(c,"UPDATE orders SET po_number='no context' WHERE id=?",order)).isZero();
            setting(c,"app.current_user_id",user);assertThat(f.db.execute(c,"UPDATE orders SET po_number='bootstrap' WHERE id=?",order)).isZero();c.rollback();
            setting(c,"app.current_tenant_id",otherTenant);assertThat(f.db.execute(c,"UPDATE orders SET po_number='foreign' WHERE id=?",order)).isZero();c.rollback();
            setting(c,"app.current_tenant_id",tenant);assertThat(f.db.execute(c,"UPDATE orders SET po_number='allowed' WHERE id=?",order)).isEqualTo(1);
            assertThatThrownBy(()->f.db.execute(c,"UPDATE orders SET tenant_id=? WHERE id=?",otherTenant,order)).isInstanceOf(java.sql.SQLException.class).extracting(e->((java.sql.SQLException)e).getSQLState()).isEqualTo("42501");c.rollback();
        }
        assertThat(f.db.value("SELECT po_number FROM orders WHERE id=?",order)).isNull();
    }
    void setting(java.sql.Connection c,String key,UUID value) throws Exception {
        try(var q=c.prepareStatement("SELECT set_config(?,?,true)")){q.setString(1,key);q.setString(2,value.toString());q.execute();}
    }
    @Test void releaseInfrastructureFailureRollsBackStatusVersionTimestampsAndEveryAllocation() throws Exception {
        var one=manifests.create(admin,tenant,null); allocation(one.id(),order); var history=children();
        var trigger="release_"+UUID.randomUUID().toString().replace("-","");
        try {
            f.db.execute("CREATE FUNCTION "+trigger+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.order_id='"+order+"'::uuid THEN RAISE EXCEPTION USING ERRCODE='08006',MESSAGE='Controlled database failure'; END IF; RETURN OLD; END $$");
            f.db.execute("CREATE TRIGGER "+trigger+" BEFORE DELETE ON manifest_orders FOR EACH ROW EXECUTE FUNCTION "+trigger+"()");
            var r=cancel("{\"expectedVersion\":0,\"reason\":\"Cancel\"}"); assertThat(r.statusCode()).isEqualTo(500); assertThat(r.body()).doesNotContain("Controlled", "08006", trigger);
            assertThat(f.db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("OPEN"); assertThat(version()).isZero();
            assertThat(f.db.value("SELECT cancelled_at FROM orders WHERE id=?",order)).isNull(); assertThat(children()).isEqualTo(history);
            assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE order_id=?",order)).isEqualTo(1L);
            assertThat(f.db.value("SELECT version FROM load_manifests WHERE id=?",one.id())).isEqualTo(0L);
        } finally { f.db.execute("DROP TRIGGER IF EXISTS "+trigger+" ON manifest_orders");f.db.execute("DROP FUNCTION IF EXISTS "+trigger+"()"); }
    }
    @Test void allocationSharedOrderLockMakesCancellationWaitAndReleaseCommittedAllocation() throws Exception {
        var m=manifests.create(admin,tenant,null); var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var add=pool.submit(()->transactions.execute(tenant,()->{ var r=manifests.allocate(admin,tenant,m.id(),0,order,null,null,null);locked.countDown();waitFor(release);return r; }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue(); var cancel=pool.submit(()->cancel("{\"expectedVersion\":0,\"reason\":\"Cancel\"}"));
            try { lockWait(); } finally { release.countDown(); }
            add.get(20,TimeUnit.SECONDS);assertThat(cancel.get(20,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE order_id=?",order)).isEqualTo(0L);assertThat(f.db.value("SELECT status FROM orders WHERE id=?",order)).isEqualTo("CANCELLED");
        } finally { release.countDown(); }
    }
    @Test void cancellationExclusiveOrderLockMakesAllocationWaitThenRejectCancelledOrder() throws Exception {
        var m=manifests.create(admin,tenant,null);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var cancel=pool.submit(()->transactions.execute(tenant,()->{var r=mutations.cancel(admin,tenant,order,Map.of("expectedVersion",0,"reason","Cancel"));locked.countDown();waitFor(release);return r;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var add=pool.submit(()->manifests.allocate(admin,tenant,m.id(),0,order,null,null,null));
            try { lockWait(); } finally {release.countDown();}
            cancel.get(20,TimeUnit.SECONDS); assertThatThrownBy(()->add.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(OrderFailure.class);
            assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE order_id=?",order)).isEqualTo(0L);
        } finally {release.countDown();}
    }
    void lockWait() { await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(f.db.value("SELECT count(*) FROM pg_stat_activity WHERE usename=? AND wait_event_type='Lock' AND query LIKE '%orders%'",RUNTIME_USERNAME)).isNotEqualTo(0L)); }
    static void waitFor(CountDownLatch l) { try { if(!l.await(20,TimeUnit.SECONDS)) {throw new IllegalStateException("Test transaction timed out");} }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);} }
}
