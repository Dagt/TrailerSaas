package com.trailersaas.orders.internal;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAssociationConcurrencyPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired OrderService orders;
    @Autowired TenantTransactionExecutor transactions;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;

    @Test void creationSharedLockMakesRemovalWaitThenSeeCommittedActiveOrder() throws Exception {
        var f = new OrderFixtures(flyway); var tenant = f.db.tenant();
        var customer = f.customer(tenant); var site = f.site(tenant, customer); var user = f.db.user();
        f.db.membership(tenant, user, "ACTIVE", "TENANT_ADMIN");
        var principal = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
        when(verifier.verify("admin")).thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var creation = executor.submit(() -> transactions.execute(tenant, () -> {
                var created = orders.create(principal, tenant, Map.of("customerId", customer.toString(),
                        "orderNumber", "ORD-lock", "requestedStops", List.of(Map.of("sequenceNumber", 1,
                                "siteId", site.toString(), "stopType", "DELIVERY"))));
                // Actual creation has validated under FOR SHARE and flushed; outer transaction owns that lock until commit.
                locked.countDown(); waitFor(release); return created.id();
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var removal = executor.submit(() -> HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/v1/tenants/" + tenant + "/customers/" + customer + "/sites/" + site))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer admin").DELETE().build(),
                    HttpResponse.BodyHandlers.ofString()));
            try {
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    try (var c = flyway.getConfiguration().getDataSource().getConnection(); var q = c.prepareStatement(
                            "SELECT count(*) FROM pg_stat_activity WHERE usename=? AND wait_event_type='Lock' AND query LIKE '%physical_sites%'")) {
                        q.setString(1, RUNTIME_USERNAME);
                        try (var rows = q.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isGreaterThan(0); }
                    }
                });
            } finally { release.countDown(); }
            var id = creation.get(15, TimeUnit.SECONDS);
            var result = removal.get(15, TimeUnit.SECONDS);
            assertThat(result.statusCode()).isEqualTo(409);
            assertThat(result.body()).contains("RESOURCE_CONFLICT").doesNotContain("SQL", "constraint");
            assertThat(f.db.value("SELECT status FROM orders WHERE id=?", id)).isEqualTo("OPEN");
            assertThat(f.db.value("SELECT count(*) FROM order_stops WHERE order_id=? AND site_id=?", id, site)).isEqualTo(1L);
            assertThat(f.db.value("SELECT count(*) FROM customer_sites WHERE tenant_id=? AND customer_id=? AND site_id=?", tenant, customer, site)).isEqualTo(1L);
        } finally { release.countDown(); }
    }
    static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out waiting for test transaction"); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
