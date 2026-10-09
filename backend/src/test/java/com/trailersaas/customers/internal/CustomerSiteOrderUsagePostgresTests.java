package com.trailersaas.customers.internal;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.trailersaas.OrderFixtures;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerSiteOrderUsagePostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired MasterDataService customers;
    @Autowired TenantTransactionExecutor transactions;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    OrderFixtures f;
    UUID tenant, customer, site, user;
    AuthenticatedUserPrincipal principal;
    @BeforeEach void seed() throws Exception {
        f = new OrderFixtures(flyway); tenant = f.db.tenant(); customer = f.customer(tenant);
        site = f.site(tenant, customer); user = f.db.user();
        f.db.membership(tenant, user, "ACTIVE", "TENANT_ADMIN");
        principal = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
        when(verifier.verify("admin")).thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void openOrderWithSiteOnlyOrNeutralLocationBlocksHttpRemoval(boolean neutral) throws Exception {
        var location = neutral ? f.location(tenant, site, null) : null;
        var created = create(location); assertThat(created.statusCode()).isEqualTo(201);
        conflict(remove());
        assertThat(associationCount()).isEqualTo(1L);
        assertThat(f.db.value("SELECT count(*) FROM orders WHERE tenant_id=?", tenant)).isEqualTo(1L);
    }
    @ParameterizedTest @ValueSource(strings = {"OPEN", "PLANNED", "IN_PROGRESS", "PARTIALLY_DELIVERED", "DELIVERED", "CANCELLING"})
    void everyAcceptedNonterminalStatusProtectsAssociation(String status) throws Exception {
        var order = f.order(tenant, customer, user);
        f.stop(tenant, order, 1, site, null, null);
        // Fixture only: no production transition is implemented.
        f.db.execute("UPDATE orders SET status=?,delivered_at=CASE WHEN ?='DELIVERED' THEN now() ELSE NULL END WHERE id=?", status, status, order);
        conflict(remove()); assertThat(associationCount()).isEqualTo(1L);
    }
    @ParameterizedTest @ValueSource(strings = {"COMPLETED", "CANCELLED"})
    void terminalOrdersPermitRemovalAndPreserveHistoricalRequestedStop(String status) throws Exception {
        var order = terminal(status); var stop = f.stop(tenant, order, 1, site, null, "{\"name\":\"Historical destination\"}");
        var before = f.db.value("SELECT row_to_json(s)::text FROM order_stops s WHERE id=?", stop);
        assertThat(remove().statusCode()).isEqualTo(204); assertThat(associationCount()).isEqualTo(0L);
        assertThat(f.db.value("SELECT row_to_json(s)::text FROM order_stops s WHERE id=?", stop)).isEqualTo(before);
        assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo(status);
    }
    @Test void terminalOrderDoesNotRemoveProtectionForActiveCustomerSpecificLocation() throws Exception {
        var order = terminal("COMPLETED"); var stop = f.stop(tenant, order, 1, site, null, null);
        var location = f.location(tenant, site, customer);
        conflict(remove()); assertThat(associationCount()).isEqualTo(1L);
        f.db.execute("UPDATE operational_locations SET active=false WHERE id=?", location);
        assertThat(remove().statusCode()).isEqualTo(204);
        assertThat(f.db.value("SELECT site_id FROM order_stops WHERE id=?", stop)).isEqualTo(site);
    }
    @Test void activeOrderForAnotherCustomerDoesNotBlockUnrelatedAssociationRemoval() throws Exception {
        var other = f.customer(tenant);
        f.db.execute("INSERT INTO customer_sites VALUES(?,?,?,?,now())", UUID.randomUUID(), tenant, other, site);
        var order = f.order(tenant, other, user); var stop = f.stop(tenant, order, 1, site, null, null);
        assertThat(remove().statusCode()).isEqualTo(204);
        assertThat(f.db.value("SELECT site_id FROM order_stops WHERE id=?", stop)).isEqualTo(site);
        assertThat(f.db.value("SELECT count(*) FROM customer_sites WHERE tenant_id=? AND customer_id=? AND site_id=?", tenant, other, site)).isEqualTo(1L);
    }
    @Test void exclusiveRemovalFirstMakesConcurrentOrderCreationWaitThenRejectsMissingAssociation() throws Exception {
        var removed = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var removal = executor.submit(() -> transactions.execute(tenant, () -> {
                customers.disassociate(principal, tenant, customer, site);
                removed.countDown(); waitFor(release); return true;
            }));
            assertThat(removed.await(10, TimeUnit.SECONDS)).isTrue();
            var creation = executor.submit(() -> create(null));
            try { awaitBlockedSiteQuery(); } finally { release.countDown(); }
            assertThat(removal.get(15, TimeUnit.SECONDS)).isTrue();
            conflict(creation.get(15, TimeUnit.SECONDS));
            assertThat(associationCount()).isEqualTo(0L);
            for (var table : List.of("orders", "order_cargo_lines", "order_stops")) {
                assertThat(f.db.value("SELECT count(*) FROM " + table + " WHERE tenant_id=?", tenant)).isEqualTo(0L);
            }
        } finally { release.countDown(); }
    }
    UUID terminal(String status) throws Exception {
        var order = f.order(tenant, customer, user);
        if (status.equals("COMPLETED")) { f.db.execute("UPDATE orders SET status='COMPLETED',delivered_at=now(),completed_at=now() WHERE id=?", order); }
        else { f.db.execute("UPDATE orders SET status='CANCELLED',cancelled_at=now() WHERE id=?", order); }
        return order;
    }
    Object associationCount() throws Exception { return f.db.value("SELECT count(*) FROM customer_sites WHERE tenant_id=? AND customer_id=? AND site_id=?", tenant, customer, site); }
    HttpResponse<String> create(UUID location) throws Exception {
        String body = "{\"customerId\":\"" + customer + "\",\"orderNumber\":\"ORD-lock\",\"requestedStops\":[{\"sequenceNumber\":1,\"siteId\":\"" + site + "\",\"locationId\":" + (location == null ? "null" : "\"" + location + "\"") + ",\"stopType\":\"DELIVERY\"}]}";
        return request("POST", "/orders", body);
    }
    HttpResponse<String> remove() throws Exception { return request("DELETE", "/customers/" + customer + "/sites/" + site, null); }
    HttpResponse<String> request(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/tenants/" + tenant + path))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer admin").header("X-Request-Id", "association-review");
        if (body != null) { builder.header("Content-Type", "application/json"); }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void conflict(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("RESOURCE_CONFLICT", "traceId").doesNotContain("constraint", "SQL", "fixture-", user.toString());
        assertThat(response.headers().firstValue("X-Request-Id")).contains("association-review");
    }
    void awaitBlockedSiteQuery() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.prepareStatement(
                    "SELECT count(*) FROM pg_stat_activity WHERE usename=? AND wait_event_type='Lock' AND query LIKE '%physical_sites%'")) {
                q.setString(1, RUNTIME_USERNAME);
                try (var rows = q.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isGreaterThan(0); }
            }
        });
    }
    static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out waiting for test transaction"); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
