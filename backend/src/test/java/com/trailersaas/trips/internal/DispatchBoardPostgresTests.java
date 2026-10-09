package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DispatchBoardPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired EntityManagerFactory emf;
  @MockitoBean FirebaseTokenVerifier verifier;

  @Value("${local.server.port}")
  int port;

  OrderFixtures f;
  UUID tenant, other, customer, secondCustomer, user, driver, tractor;

  @BeforeEach
  void seed() throws Exception {
    f = new OrderFixtures(flyway);
    tenant = f.db.tenant();
    other = f.db.tenant();
    customer = f.customer(tenant);
    secondCustomer = f.customer(tenant);
    driver = f.db.driver(tenant, null);
    tractor = f.db.tractor(tenant, "Board Tractor");
    user = actor("admin", "TENANT_ADMIN");
    actor("ops", "TRANSPORT_OPERATIONS");
    actor("dispatcher", "CUSTOMER_DISPATCHER");
    actor("yard", "YARD_SPOTTER");
    actor("driver", "DRIVER");
  }

  UUID actor(String token, String role) throws Exception {
    var id = f.db.user();
    f.db.membership(tenant, id, "ACTIVE", role);
    when(verifier.verify(token))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null));
    return id;
  }

  HttpResponse<String> get(UUID t, String query, String token) throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:"
                        + port
                        + "/api/v1/tenants/"
                        + t
                        + "/dispatch/board"
                        + query))
            .header("X-Request-Id", "board-regression");
    if (token != null) request.header("Authorization", "Bearer " + token);
    return HttpClient.newHttpClient()
        .send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);
    assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
    assertThat(r.body()).contains("traceId").doesNotContain("SQL", "constraint", "fixture-");
    assertThat(r.headers().firstValue("X-Request-Id")).contains("board-regression");
  }

  UUID trip(UUID t, String status, UUID d, UUID tr) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO"
            + " trips(id,tenant_id,status,current_driver_id,current_tractor_id,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,now(),now())",
        id,
        t,
        status,
        d,
        tr);
    return id;
  }

  UUID stop(UUID t, UUID trip, int seq, String state, String name) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO"
            + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at)"
            + " VALUES(?,?,?,?,'DELIVERY',?,?::jsonb,now(),now())",
        id,
        t,
        trip,
        seq,
        state,
        "{\"name\":\"" + name + "\"}");
    return id;
  }

  void map(UUID t, UUID s, UUID order) throws Exception {
    f.db.execute(
        "INSERT INTO trip_stop_orders VALUES(?,?,?,?,NULL)", UUID.randomUUID(), t, s, order);
  }

  UUID order(String status) throws Exception {
    var id = f.order(tenant, customer, user);
    f.db.execute(
        "UPDATE orders SET status=?,delivered_at=CASE WHEN ? IN ('DELIVERED','COMPLETED') THEN"
            + " now() END,completed_at=CASE WHEN ?='COMPLETED' THEN now() END,cancelled_at=CASE"
            + " WHEN ?='CANCELLED' THEN now() END WHERE id=?",
        status,
        status,
        status,
        status,
        id);
    return id;
  }

  @Test
  void authenticatedAndTenantAuthorizationRemainRequired() throws Exception {
    problem(get(tenant, "", null), 401, "AUTHENTICATION_REQUIRED");
    problem(get(other, "", "admin"), 403, "TENANT_ACCESS_DENIED");
    assertThat(get(tenant, "", "admin").statusCode()).isEqualTo(200);
    assertThat(get(tenant, "", "ops").statusCode()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"dispatcher", "yard", "driver"})
  void customerAndExecutionRolesCannotReadTheBoard(String token) throws Exception {
    problem(get(tenant, "", token), 403, "CAPABILITY_DENIED");
  }

  @Test
  void pendingUsesOperationalPlanningNotManifestAndExcludesTerminalOrders() throws Exception {
    var pending = order("OPEN");
    var planned = order("PLANNED");
    order("COMPLETED");
    order("CANCELLED");
    var t = trip(tenant, "PLANNED", null, null);
    map(tenant, stop(tenant, t, 1, "PENDING", "Operational"), planned);
    var manifest = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO load_manifests(id,tenant_id,status,created_at,updated_at)"
            + " VALUES(?,?,'OPEN',now(),now())",
        manifest,
        tenant);
    f.db.execute(
        "INSERT INTO manifest_orders(id,tenant_id,manifest_id,order_id,created_at)"
            + " VALUES(?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        manifest,
        pending);
    var r = get(tenant, "", "admin");
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    assertThat(JsonPath.<List<String>>read(r.body(), "$.pendingOrders[*].id"))
        .containsExactly(pending.toString());
    assertThat(r.body())
        .contains("generatedAt")
        .doesNotContain("createdByUserId", "assignmentHistory", "operationalHistory", "tenantId");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "ASSIGNED", "IN_TRANSIT", "AT_STOP", "CANCELLING"})
  void includesEveryAuthoritativeActiveTripStatus(String status) throws Exception {
    var id = trip(tenant, status, driver, tractor);
    var r = get(tenant, "", "admin");
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    assertThat(JsonPath.<List<String>>read(r.body(), "$.activeTrips[*].id"))
        .containsExactly(id.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"COMPLETED", "CANCELLED"})
  void terminalTripsAreExcludedAndTheirMappingsDoNotPlanPendingWork(String state) throws Exception {
    UUID o = order("OPEN"), t = trip(tenant, state, null, null);
    map(tenant, stop(tenant, t, 1, "COMPLETED", "Historical"), o);
    var r = get(tenant, "", "admin");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.activeTrips[*].id")).isEmpty();
    assertThat(JsonPath.<List<String>>read(r.body(), "$.pendingOrders[*].id"))
        .containsExactly(o.toString());
  }

  @Test
  void firstNonterminalStopComesFromCurrentSequenceNotHistoryOrGps() throws Exception {
    var t = trip(tenant, "ASSIGNED", driver, tractor);
    stop(tenant, t, 1, "COMPLETED", "Finished");
    var next = stop(tenant, t, 3, "PENDING", "Next planned visit");
    stop(tenant, t, 7, "EN_ROUTE", "Later");
    var r = get(tenant, "", "admin");
    assertThat(JsonPath.<String>read(r.body(), "$.activeTrips[0].nextPlannedStop.id"))
        .isEqualTo(next.toString());
    assertThat(JsonPath.<String>read(r.body(), "$.activeTrips[0].nextPlannedStop.snapshotName"))
        .isEqualTo("Next planned visit");
  }

  @Test
  void attentionReflectsOnlyIncompletePlannedAndCurrentCancellation() throws Exception {
    UUID incomplete = trip(tenant, "PLANNED", null, tractor),
        cancelling = trip(tenant, "CANCELLING", driver, tractor);
    trip(tenant, "PLANNED", driver, tractor);
    trip(tenant, "ASSIGNED", null, null);
    var r = get(tenant, "", "admin");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.attentionItems[*].type"))
        .containsExactlyInAnyOrder("INCOMPLETE_ASSIGNMENT", "CANCELLING");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.attentionItems[*].tripId"))
        .containsExactlyInAnyOrder(incomplete.toString(), cancelling.toString());
  }

  @Test
  void boardIsTenantScopedAndCustomerDriverAndStatusFiltersAreConjunctive() throws Exception {
    var p = order("OPEN");
    f.order(tenant, secondCustomer, user);
    var t = trip(tenant, "ASSIGNED", driver, tractor);
    map(tenant, stop(tenant, t, 1, "PENDING", "Here"), p);
    trip(tenant, "PLANNED", null, null);
    var foreign = trip(other, "PLANNED", null, null);
    var r =
        get(tenant, "?customerId=" + customer + "&driverId=" + driver + "&status=ASSIGNED", "ops");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.activeTrips[*].id"))
        .containsExactly(t.toString());
    assertThat(JsonPath.<List<String>>read(r.body(), "$.pendingOrders[*].id")).isEmpty();
    assertThat(get(tenant, "", "admin").body()).doesNotContain(foreign.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "?date=2026-10-09",
        "?limit=1000",
        "?status=unknown",
        "?driverId=invalid",
        "?customerId=invalid",
        "?active=true"
      })
  void unknownOrMalformedFiltersFailSafely(String query) throws Exception {
    problem(get(tenant, query, "admin"), 400, "VALIDATION_FAILED");
  }

  @Test
  void boundTwentyResultsAndConstantCurrentProjectionQueriesWithoutEventReplay() throws Exception {
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    for (int i = 0; i < 25; i++) {
      order("OPEN");
      var t = trip(tenant, "PLANNED", null, null);
      stop(tenant, t, 1, "PENDING", "Next");
    }
    statistics.clear();
    var r = get(tenant, "", "admin");
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(200);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.pendingOrders")).hasSize(20);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.activeTrips")).hasSize(20);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.attentionItems")).hasSize(20);
    assertThat(statistics.getPrepareStatementCount()).isLessThan(15);
    assertThat(
            statistics
                .getEntityStatistics(TripAssignmentEventEntity.class.getName())
                .getLoadCount())
        .isZero();
    assertThat(
            statistics
                .getEntityStatistics(TripOperationalEventEntity.class.getName())
                .getLoadCount())
        .isZero();
    var first = JsonPath.<List<String>>read(r.body(), "$.activeTrips[*].id");
    assertThat(JsonPath.<List<String>>read(get(tenant, "", "admin").body(), "$.activeTrips[*].id"))
        .containsExactlyElementsOf(first);
    var fields = new ArrayList<String>();
    new ObjectMapper().readTree(r.body()).fieldNames().forEachRemaining(fields::add);
    assertThat(fields)
        .containsExactly("generatedAt", "pendingOrders", "activeTrips", "attentionItems");
  }

  @Test
  void databaseMembershipChangesTakeEffectWithoutNewFirebaseToken() throws Exception {
    assertThat(get(tenant, "", "admin").statusCode()).isEqualTo(200);
    f.db.execute(
        "UPDATE tenant_memberships SET status='SUSPENDED' WHERE tenant_id=? AND user_id=?",
        tenant,
        user);
    problem(get(tenant, "", "admin"), 403, "TENANT_ACCESS_DENIED");
  }
}
