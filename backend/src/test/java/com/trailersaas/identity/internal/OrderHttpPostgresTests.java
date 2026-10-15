package com.trailersaas.identity.internal;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import com.jayway.jsonpath.JsonPath;
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
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderHttpPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    OrderFixtures f;
    UUID tenant, foreignTenant, a, b, c, foreignCustomer, site, neutral, own, foreignLocation, otherSite, otherLocation, user, dispatcher, dispatcherMembership;
    @BeforeEach void seed() throws Exception {
        f = new OrderFixtures(flyway); tenant = f.db.tenant(); foreignTenant = f.db.tenant();
        f.db.execute("UPDATE tenants SET status='ACTIVE' WHERE id=?",tenant);
        a = f.customer(tenant); b = f.customer(tenant); c = f.customer(tenant); foreignCustomer = f.customer(foreignTenant);
        site = f.site(tenant, a); neutral = f.location(tenant, site, null); own = f.location(tenant, site, a);
        f.db.execute("INSERT INTO customer_sites VALUES(?,?,?,?,now())", UUID.randomUUID(), tenant, b, site);
        foreignLocation = f.location(tenant, site, b); otherSite = f.site(tenant, a); otherLocation = f.location(tenant, otherSite, a);
        user = actor("admin", "TENANT_ADMIN"); actor("ops", "TRANSPORT_OPERATIONS"); actor("yard", "YARD_SPOTTER"); actor("driver", "DRIVER");
        dispatcher = f.db.user(); dispatcherMembership = f.db.membership(tenant, dispatcher, "ACTIVE", null);
        for (var customer : List.of(a, c)) { f.db.execute("INSERT INTO role_assignments VALUES(?,?,?,'CUSTOMER_DISPATCHER','CUSTOMER',?,now())", UUID.randomUUID(), tenant, dispatcherMembership, customer); }
        identity("dispatcher", dispatcher);
    }
    UUID actor(String token, String role) throws Exception { var id = f.db.user(); f.db.membership(tenant, id, "ACTIVE", role); identity(token, id); return id; }
    void identity(String token, UUID id) { when(verifier.verify(token)).thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null)); }
    String adhoc(int sequence) { return "{\"sequenceNumber\":" + sequence + ",\"stopType\":\"DELIVERY\",\"siteSnapshot\":{\"name\":\"Receiving\",\"address\":{\"line1\":\"710 Kimberly Dr\"},\"timezone\":\"America/Chicago\"}}"; }
    String create(UUID customer, String stops, String cargo) { return "{\"customerId\":\"" + customer + "\",\"orderNumber\":\"ORD-123\",\"poNumber\":\"PO-88\",\"shipmentNumber\":\"SHIP-2\",\"bolReference\":\"BOL-456\",\"cargoLines\":" + cargo + ",\"requestedStops\":[" + stops + "]}"; }
    String simple(UUID customer) { return create(customer, adhoc(1), "[]"); }
    String normalized(UUID siteId, UUID location) { return "{\"sequenceNumber\":1,\"siteId\":\"" + siteId + "\",\"locationId\":" + (location == null ? "null" : "\"" + location + "\"") + ",\"stopType\":\"DELIVERY\"}"; }
    String planned(UUID trailer) { return simple(a).replace("{\"customerId\"", "{\"plannedTrailerId\":\"" + trailer + "\",\"customerId\""); }
    @ParameterizedTest @ValueSource(strings = {"admin", "ops", "dispatcher"})
    void plannedTrailerCreatesOpenManifestAndNullAllocationAtomicallyWithoutPhysicalChanges(String token) throws Exception {
        var trailer = new com.trailersaas.TrailerFixtures(flyway).trailer(tenant, "Plan-" + UUID.randomUUID(), a);
        var response = post(token, planned(trailer)); var order = id(response);
        assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("OPEN");
        assertThat(response.body()).doesNotContain("plannedTrailerId", "manifestId", "createdByUserId");
        assertThat(f.db.value("SELECT count(*) FROM load_manifests m JOIN manifest_orders a ON a.tenant_id=m.tenant_id AND a.manifest_id=m.id WHERE a.order_id=? AND m.trailer_id=? AND m.status='OPEN' AND m.version=0 AND m.closed_at IS NULL AND a.allocated_quantity IS NULL AND a.allocated_pallet_count IS NULL AND a.allocated_weight IS NULL", order, trailer)).isEqualTo(1L);
        assertThat(f.db.value("SELECT count(*) FROM trailers WHERE id=? AND active AND location_state='LOCATION_UNKNOWN' AND current_location_id IS NULL AND last_known_location_id IS NULL AND last_driver_id IS NULL AND last_seen_at IS NULL AND load_state='UNKNOWN' AND serviceability_state='IN_SERVICE' AND version=0", trailer)).isEqualTo(1L);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "null"})
    void withoutPlannedTrailerNoManifestIsCreated(String mode) throws Exception {
        var body = simple(a); if (mode.equals("null")) { body = body.replace("{\"customerId\"", "{\"plannedTrailerId\":null,\"customerId\""); }
        id(post("admin", body));
        assertThat(f.db.value("SELECT count(*) FROM load_manifests WHERE tenant_id=?", tenant)).isEqualTo(0L);
        assertThat(f.db.value("SELECT count(*) FROM manifest_orders WHERE tenant_id=?", tenant)).isEqualTo(0L);
    }
    @ParameterizedTest @ValueSource(strings = {"inactive", "oos", "foreignTenant", "missing", "foreignCustomer", "unassigned"})
    void incompatibleOrInvisibleTrailerNeverCreatesAnyPlanningRows(String mode) throws Exception {
        var targetTenant = mode.equals("foreignTenant") ? foreignTenant : tenant;
        var customer = mode.equals("foreignTenant") ? foreignCustomer : mode.equals("foreignCustomer") ? b : mode.equals("unassigned") ? null : a;
        var trailer = new com.trailersaas.TrailerFixtures(flyway).trailer(targetTenant, "Plan-" + UUID.randomUUID(), customer);
        if (mode.equals("inactive")) { f.db.execute("UPDATE trailers SET active=false WHERE id=?", trailer); }
        if (mode.equals("oos")) { f.db.execute("UPDATE trailers SET serviceability_state='OUT_OF_SERVICE' WHERE id=?", trailer); }
        var response = post("dispatcher", planned(mode.equals("missing") ? UUID.randomUUID() : trailer));
        switch (mode) {
            case "inactive", "oos" -> problem(response, 409, "RESOURCE_CONFLICT");
            case "foreignCustomer", "unassigned" -> problem(response, 403, "CUSTOMER_SCOPE_DENIED");
            default -> problem(response, 404, "RESOURCE_NOT_FOUND");
        }
        for (var table : List.of("orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders")) {
            assertThat(f.db.value("SELECT count(*) FROM " + table + " WHERE tenant_id=?", tenant)).isEqualTo(0L);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"manifest", "allocation"})
    void planningIntegrityFailureRollsBackOrderCargoStopsAndManifest(String kind) throws Exception { planningRollback(kind, "23514", 409); }
    @ParameterizedTest @ValueSource(strings = {"manifest", "allocation"})
    void planningInfrastructureFailureRemains5xxWithAtomicRollback(String kind) throws Exception { planningRollback(kind, "08006", 500); }
    void planningRollback(String kind, String state, int status) throws Exception {
        var trailer = new com.trailersaas.TrailerFixtures(flyway).trailer(tenant, "Rollback-" + UUID.randomUUID(), a);
        String fn = "planning_fail_" + UUID.randomUUID().toString().replace("-", "");
        String table = kind.equals("manifest") ? "load_manifests" : "manifest_orders";
        f.db.execute("CREATE FUNCTION " + fn + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id='" + tenant + "' THEN RAISE EXCEPTION 'controlled planning failure' USING ERRCODE='" + state + "'; END IF; RETURN NEW; END $$");
        f.db.execute("CREATE TRIGGER " + fn + " BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION " + fn + "()");
        try {
            var body = create(a, adhoc(1), "[{\"description\":\"Cargo\"}]").replace("{\"customerId\"", "{\"plannedTrailerId\":\"" + trailer + "\",\"customerId\"");
            var response = post("admin", body); assertThat(response.statusCode()).isEqualTo(status);
            assertThat(response.body()).contains("traceId").doesNotContain(fn, "controlled planning failure", state);
            if (status == 409) { problem(response, status, "RESOURCE_CONFLICT"); }
            for (var child : List.of("orders", "order_cargo_lines", "order_stops", "load_manifests", "manifest_orders")) { assertThat(f.db.value("SELECT count(*) FROM " + child + " WHERE tenant_id=?", tenant)).isEqualTo(0L); }
        } finally { f.db.execute("DROP TRIGGER " + fn + " ON " + table); f.db.execute("DROP FUNCTION " + fn + "()"); }
    }
    @ParameterizedTest @ValueSource(strings = {"admin", "ops", "dispatcher"})
    void createIsAtomicOpenIndependentOfTripAndSupportsMultipleStopsAndCargo(String token) throws Exception {
        var result = post(token, create(a, adhoc(2) + "," + adhoc(1), "[{\"description\":\"Sheets\",\"quantity\":20.5,\"palletCount\":10,\"weight\":18500,\"weightUnit\":\"LB\"},{\"description\":\"Other\",\"weightUnit\":\"custom unit\"}]"));
        assertThat(result.statusCode()).isEqualTo(201); var id = id(result);
        assertThat(JsonPath.<String>read(result.body(), "$.status")).isEqualTo("OPEN");
        assertThat(JsonPath.<Integer>read(result.body(), "$.version")).isZero();
        for (var field : List.of("deliveredAt", "completedAt", "cancelledAt")) { assertThat(JsonPath.<Object>read(result.body(), "$." + field)).isNull(); }
        assertThat(JsonPath.<List<Integer>>read(result.body(), "$.requestedStops[*].sequenceNumber")).containsExactly(1, 2);
        assertThat(JsonPath.<List<?>>read(result.body(), "$.cargoLines")).hasSize(2);
        assertThat(JsonPath.<List<String>>read(result.body(), "$.cargoLines[*].weightUnit")).containsExactlyInAnyOrder("LB", "custom unit");
        assertThat(f.db.value("SELECT count(*) FROM orders WHERE id=?", id)).isEqualTo(1L);
        assertThat(f.db.value("SELECT count(*) FROM order_cargo_lines WHERE order_id=?", id)).isEqualTo(2L);
        assertThat(f.db.value("SELECT count(*) FROM order_stops WHERE order_id=?", id)).isEqualTo(2L);
        assertThat(JsonPath.<Map<String,Object>>read(get(token, "/" + id).body(), "$")).isEqualTo(JsonPath.<Map<String,Object>>read(result.body(), "$"));
        assertThat(result.body()).doesNotContain("plannedTrailer", "tripId", "manifest", "fulfillment", "tenantId");
    }
    @Test void zeroCargoAndRepeatedOrderNumberAreAllowed() throws Exception {
        var one = post("admin", simple(a)); var two = post("admin", simple(a));
        assertThat(one.statusCode()).isEqualTo(201); assertThat(two.statusCode()).isEqualTo(201);
        assertThat(id(one)).isNotEqualTo(id(two)); assertThat(JsonPath.<List<?>>read(one.body(), "$.cargoLines")).isEmpty();
    }
    @Test void publicResponsesNeverExposeCreatorIdentityButPersistServerPrincipal() throws Exception {
        var adminOrder = post("admin", simple(a)); var opsOrder = post("ops", simple(a));
        var dispatcherOrder = post("dispatcher", simple(a));
        var adminId = id(adminOrder);
        assertThat(f.db.value("SELECT created_by_user_id FROM orders WHERE id=?", adminId)).isEqualTo(user);
        assertThat(f.db.value("SELECT created_by_user_id FROM orders WHERE id=?", id(dispatcherOrder))).isEqualTo(dispatcher);
        for (var response : List.of(adminOrder, opsOrder, dispatcherOrder, get("dispatcher", ""), get("dispatcher", "/" + adminId))) {
            assertThat(response.statusCode()).isBetween(200, 201);
            assertThat(response.body()).doesNotContain("createdByUserId", "firebaseUid", "userId", "creatorEmail", "creatorDisplayName", user.toString(), dispatcher.toString());
        }
    }
    @Test void customerScopeCannotBeSpoofedInCreateListDetailOrCursor() throws Exception {
        var aOrder = id(post("admin", simple(a))); var bOrder = id(post("admin", simple(b))); var cOrder = id(post("admin", simple(c)));
        problem(post("dispatcher", simple(b)), 403, "CUSTOMER_SCOPE_DENIED");
        var list = get("dispatcher", "");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(list.body(), "$.items[*].id")).containsExactlyInAnyOrder(aOrder.toString(), cOrder.toString()).doesNotContain(bOrder.toString());
        problem(get("dispatcher", "?customerId=" + b), 403, "CUSTOMER_SCOPE_DENIED");
        var hidden = get("dispatcher", "/" + bOrder); problem(hidden, 404, "RESOURCE_NOT_FOUND");
        assertThat(JsonPath.<String>read(hidden.body(), "$.detail")).isEqualTo("The resource was not found.");
        assertThat(hidden.body()).doesNotContain(b.toString(), "ORD-123", "PO-88");
        assertThat(get("dispatcher", "/" + aOrder).statusCode()).isEqualTo(200);
        // A cursor obtained from Tenant-wide data cannot widen the Customer filter.
        var cursor = JsonPath.<String>read(get("admin", "?limit=1").body(), "$.nextCursor");
        var paged = get("dispatcher", "?limit=1&cursor=" + cursor);
        assertThat(JsonPath.<List<String>>read(paged.body(), "$.items[*].customerId")).allMatch(v -> Set.of(a.toString(), c.toString()).contains(v));
    }
    @Test void crossTenantReferencesAndDirectIdsDoNotLeak() throws Exception {
        problem(post("admin", simple(foreignCustomer)), 409, "RESOURCE_CONFLICT");
        var foreignOrder = f.order(foreignTenant, foreignCustomer, user);
        problem(get("admin", "/" + foreignOrder), 404, "RESOURCE_NOT_FOUND");
        var foreignSite = f.site(foreignTenant, foreignCustomer); var location = f.location(foreignTenant, foreignSite, foreignCustomer);
        problem(post("admin", create(a, normalized(foreignSite, location), "[]")), 409, "RESOURCE_CONFLICT");
        problem(request("admin", "GET", foreignTenant, "", null), 403, "TENANT_ACCESS_DENIED");
        assertThat(f.db.value("SELECT count(*) FROM orders WHERE tenant_id=?", tenant)).isEqualTo(0L);
    }
    @Test void normalizedDestinationsRequireCustomerAssociationAndExactSiteLocationCompatibility() throws Exception {
        for (var destination : List.of(normalized(site, own), normalized(site, neutral), normalized(site, null))) {
            assertThat(post("dispatcher", create(a, destination, "[]")).statusCode()).isEqualTo(201);
        }
        for (var destination : List.of(normalized(site, foreignLocation), normalized(site, otherLocation), normalized(f.site(tenant, b), null), normalized(UUID.randomUUID(), null))) {
            problem(post("admin", create(a, destination, "[]")), 409, "RESOURCE_CONFLICT");
        }
        f.db.execute("UPDATE customers SET status='INACTIVE' WHERE id=?", a);
        assertThat(post("admin", simple(a)).statusCode()).isEqualTo(201); // No invented ACTIVE Customer eligibility rule.
    }
    @Test void frozenSnapshotCoexistsWithNormalizedSiteAndSurvivesMasterEdits() throws Exception {
        String stop = normalized(site, own).replace("\"stopType\"", "\"siteSnapshot\":{\"name\":\"Original destination\",\"address\":{\"line1\":\"710 Kimberly Dr\"}},\"stopType\"");
        var result = post("dispatcher", create(a, stop, "[]")); assertThat(result.statusCode()).isEqualTo(201);
        f.db.execute("UPDATE physical_sites SET name='Changed',address_line1='Changed',version=version+1 WHERE id=?", site);
        var detail = get("dispatcher", "/" + id(result));
        assertThat(JsonPath.<String>read(detail.body(), "$.requestedStops[0].siteSnapshot.name")).isEqualTo("Original destination");
        assertThat(JsonPath.<String>read(detail.body(), "$.requestedStops[0].siteSnapshot.address.line1")).isEqualTo("710 Kimberly Dr");
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "noDestination", "locationWithoutSite", "duplicateSequence", "zeroSequence", "badDate", "snapshotArray", "snapshotIdentity"})
    void invalidRequestedStopsAreSafeValidationFailures(String invalid) throws Exception {
        String stop = switch (invalid) {
            case "empty" -> "{\"sequenceNumber\":1,\"stopType\":\"DELIVERY\",\"siteSnapshot\":{}}";
            case "noDestination" -> "{\"sequenceNumber\":1,\"stopType\":\"DELIVERY\"}";
            case "locationWithoutSite" -> adhoc(1).replace("\"stopType\"", "\"locationId\":\"" + own + "\",\"stopType\"");
            case "duplicateSequence" -> adhoc(1) + "," + adhoc(1);
            case "zeroSequence" -> adhoc(0);
            case "badDate" -> adhoc(1).replace("\"stopType\"", "\"scheduledAt\":\"2026-10-08T14:00:00\",\"stopType\"");
            case "snapshotArray" -> "{\"sequenceNumber\":1,\"stopType\":\"DELIVERY\",\"siteSnapshot\":[]}";
            default -> "{\"sequenceNumber\":1,\"stopType\":\"DELIVERY\",\"siteSnapshot\":{\"name\":\"Receiving\",\"customerId\":\"" + b + "\"}}";
        };
        problem(post("dispatcher", create(a, stop, "[]")), 400, "VALIDATION_FAILED");
        assertThat(f.db.value("SELECT count(*) FROM orders WHERE tenant_id=?", tenant)).isEqualTo(0L);
    }
    @ParameterizedTest @ValueSource(strings = {"plannedTrailerId", "status", "tenantId", "version"})
    void futureOrServerManagedCreateFieldsAreRejected(String field) throws Exception {
        problem(post("admin", simple(a).replace("{\"customerId\"", "{\"" + field + "\":\"private\",\"customerId\"")), 400, "VALIDATION_FAILED");
    }
    @ParameterizedTest @ValueSource(strings = {"unplanned=maybe", "active=true", "appointmentFrom=2026-10-08T00:00:00Z", "unknown=value", "limit=0", "limit=201", "cursor=unknown", "status=INVALID", "customerId=unknown"})
    void unsupportedOrMalformedFiltersAreRejected(String query) throws Exception { problem(get("admin", "?" + query), 400, "VALIDATION_FAILED"); }
    @Test void cursorIsRealStableKeysetIncludingTiedTimestampsAndCustomerStatusFilters() throws Exception {
        var ids = new ArrayList<UUID>(); for (int i = 0; i < 5; i++) { ids.add(id(post("admin", simple(a)))); }
        id(post("admin", simple(b)));
        f.db.execute("UPDATE orders SET created_at='2026-10-08T12:00:00Z' WHERE tenant_id=?", tenant);
        var all = new ArrayList<String>(); String cursor = null;
        do {
            var response = get("ops", "?customerId=" + a + "&status=OPEN&limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
            assertThat(response.statusCode()).isEqualTo(200);
            var items = JsonPath.<List<String>>read(response.body(), "$.items[*].id"); assertThat(items).hasSizeLessThanOrEqualTo(2);
            all.addAll(items); cursor = JsonPath.read(response.body(), "$.nextCursor");
        } while (cursor != null);
        var expected = ids.stream().map(UUID::toString).sorted(Comparator.reverseOrder()).toList();
        assertThat(all).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(JsonPath.<List<?>>read(get("ops", "?status=COMPLETED").body(), "$.items")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"SUSPENDED", "DISABLED", "REVOKED_ROLE"})
    void sameFirebaseTokenLosesAccessAfterPersistedStatusOrRoleChange(String change) throws Exception {
        var order = id(post("dispatcher", simple(a)));
        if (change.equals("REVOKED_ROLE")) { f.db.execute("DELETE FROM role_assignments WHERE membership_id=?", dispatcherMembership); }
        else { f.db.execute("UPDATE tenant_memberships SET status=? WHERE id=?", change, dispatcherMembership); }
        String code = change.equals("REVOKED_ROLE") ? "CAPABILITY_DENIED" : "TENANT_ACCESS_DENIED";
        problem(post("dispatcher", simple(a)), 403, code); problem(get("dispatcher", ""), 403, code); problem(get("dispatcher", "/" + order), 403, code);
    }
    @ParameterizedTest @ValueSource(strings = {"driver", "yard"})
    void unrelatedRolesCannotCreateOrBrowse(String token) throws Exception { problem(post(token, simple(a)), 403, "CAPABILITY_DENIED"); problem(get(token, ""), 403, "CAPABILITY_DENIED"); }
    @Test void maliciousFirebaseClaimsDoNotAuthorizeAndBearerIsNeverPublic() throws Exception {
        var attacker = f.db.user(); f.db.membership(tenant, attacker, "ACTIVE", null);
        var sdk = mock(com.google.firebase.auth.FirebaseToken.class);
        when(sdk.getUid()).thenReturn("fixture-" + attacker);
        when(sdk.getClaims()).thenReturn(Map.of("role", "TENANT_ADMIN", "customerId", a.toString(), "tenantId", tenant.toString(), "isAdmin", true));
        var identity = FirebaseAdminTokenVerifier.identity(sdk);
        when(verifier.verify("synthetic-secret-firebase-token")).thenReturn(identity);
        problem(post("synthetic-secret-firebase-token", simple(a)), 403, "CAPABILITY_DENIED");
        problem(get("synthetic-secret-firebase-token", ""), 403, "CAPABILITY_DENIED"); verify(sdk, never()).getClaims();
        problem(get(null, ""), 401, "AUTHENTICATION_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings = {"cargo", "stop"})
    void domainIntegrityFailureRollsBackOrderAndEveryChild(String table) throws Exception { rollback(table, "23514", 409, "RESOURCE_CONFLICT"); }
    @ParameterizedTest @ValueSource(strings = {"cargo", "stop"})
    void infrastructureFailureRemains5xxAndStillRollsBackEveryChild(String table) throws Exception { rollback(table, "08006", 500, "INTERNAL_ERROR"); }
    void rollback(String type, String state, int expected, String code) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", ""); String function = "order_fail_" + suffix;
        String table = type.equals("cargo") ? "order_cargo_lines" : "order_stops";
        String condition = type.equals("cargo") ? "NEW.description='Fail'" : "NEW.sequence_number=2";
        f.db.execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id='" + tenant + "' AND " + condition + " THEN RAISE EXCEPTION 'controlled database failure' USING ERRCODE='" + state + "'; END IF; RETURN NEW; END $$");
        f.db.execute("CREATE TRIGGER " + function + " BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        try {
            var response = post("admin", create(a, adhoc(1) + "," + adhoc(2), "[{\"description\":\"First\"},{\"description\":\"Fail\"}]"));
            assertThat(response.statusCode()).isEqualTo(expected);
            if (expected == 409) { problem(response, expected, code); } else { assertThat(response.body()).contains("traceId").doesNotContain("controlled database failure", "08006", function); }
            for (var child : List.of("orders", "order_cargo_lines", "order_stops")) { assertThat(f.db.value("SELECT count(*) FROM " + child + " WHERE tenant_id=?", tenant)).isEqualTo(0L); }
        } finally { f.db.execute("DROP TRIGGER " + function + " ON " + table); f.db.execute("DROP FUNCTION " + function + "()"); }
    }
    UUID id(HttpResponse<String> response) { assertThat(response.statusCode()).isEqualTo(201); return UUID.fromString(JsonPath.read(response.body(), "$.id")); }
    HttpResponse<String> post(String token, String body) throws Exception { return request(token, "POST", tenant, "", body); }
    HttpResponse<String> get(String token, String path) throws Exception { return request(token, "GET", tenant, path, null); }
    HttpResponse<String> request(String token, String method, UUID tenantId, String path, String body) throws Exception {
        var uri = URI.create("http://127.0.0.1:" + port + "/api/v1/tenants/" + tenantId + "/orders" + path);
        var builder = HttpRequest.newBuilder(uri).header("X-Request-Id", "order-correlation");
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        if (body != null) { builder.header("Content-Type", "application/json"); }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    void problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status); assertThat(response.body()).contains(code, "traceId").doesNotContain("synthetic-secret-firebase-token", "SQL", "constraint", "fixture-");
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("order-correlation");
    }
}
