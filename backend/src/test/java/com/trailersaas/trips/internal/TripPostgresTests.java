package com.trailersaas.trips.internal;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import com.trailersaas.orders.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.context.event.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RecordApplicationEvents
@org.springframework.context.annotation.Import(TripPostgresTests.EventConfiguration.class)
class TripPostgresTests extends PostgresIntegrationTest {
  @Autowired Flyway flyway;
  @Autowired DataSource runtime;
  @Autowired TripService service;
  @Autowired TenantTransactionExecutor transactions;
  @Autowired ApplicationEvents events;
  @Autowired CommittedEvents committed;
  @MockitoBean FirebaseTokenVerifier verifier;
  @MockitoSpyBean TripSiteUsageGuard usage;
  @MockitoSpyBean TripPlanningRelease releaseHandler;

  @Value("${local.server.port}")
  int port;

  OrderFixtures f;
  UUID tenant,
      other,
      customer,
      customerB,
      user,
      order,
      orderB,
      site,
      neutral,
      driver,
      tractor,
      trailer,
      membership;
  AuthenticatedUserPrincipal admin;

  @BeforeEach
  void seed() throws Exception {
    reset(usage, releaseHandler);
    f = new OrderFixtures(flyway);
    tenant = f.db.tenant();
    other = f.db.tenant();
    customer = f.customer(tenant);
    customerB = f.customer(tenant);
    user = actor("admin", "TENANT_ADMIN");
    membership =
        (UUID)
            f.db.value(
                "SELECT id FROM tenant_memberships WHERE tenant_id=? AND user_id=?", tenant, user);
    admin = new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
    actor("ops", "TRANSPORT_OPERATIONS");
    actor("yard", "YARD_SPOTTER");
    actor("driver", "DRIVER");
    actor("dispatcher", "CUSTOMER_DISPATCHER");
    order = f.order(tenant, customer, user);
    orderB = f.order(tenant, customerB, user);
    f.stop(tenant, order, 1, null, null, "{\"name\":\"Requested frozen\"}");
    f.stop(tenant, orderB, 1, null, null, "{\"name\":\"Other requested\"}");
    site = f.site(tenant, customer);
    f.db.execute(
        "INSERT INTO customer_sites VALUES(?,?,?,?,now())",
        UUID.randomUUID(),
        tenant,
        customerB,
        site);
    neutral = f.location(tenant, site, null);
    driver = f.db.driver(tenant, null);
    tractor = f.db.tractor(tenant, "T-" + UUID.randomUUID());
    trailer = new TrailerFixtures(flyway).trailer(tenant, "R-" + UUID.randomUUID(), customer);
  }

  @AfterEach
  void resetGuard() {
    reset(usage, releaseHandler);
  }

  UUID actor(String token, String role) throws Exception {
    var id = f.db.user();
    f.db.membership(tenant, id, "ACTIVE", role);
    when(verifier.verify(token))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + id, null, null));
    return id;
  }

  Map<String, Object> stop(int sequence, List<UUID> orders) {
    return new HashMap<>(
        Map.of(
            "sequenceNumber",
            sequence,
            "stopType",
            "DELIVERY",
            "orderIds",
            orders.stream().map(UUID::toString).toList(),
            "siteSnapshot",
            Map.of("name", "Frozen executable", "latitude", 32.5)));
  }

  Map<String, Object> plan() {
    return new HashMap<>(
        Map.of("orderIds", List.of(order.toString()), "stops", List.of(stop(2, List.of(order)))));
  }

  String json(Map<String, Object> body) throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
  }

  HttpResponse<String> http(String method, String path, String token, String body)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("X-Request-Id", "trip-regression");
    if (token != null) {
      b.header("Authorization", "Bearer " + token);
    }
    return HttpClient.newHttpClient()
        .send(
            b.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
  }

  String base() {
    return "/api/v1/tenants/" + tenant;
  }

  HttpResponse<String> post(Map<String, Object> b) throws Exception {
    return http("POST", base() + "/trips", "admin", json(b));
  }

  UUID id(HttpResponse<String> r) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(201);
    return UUID.fromString(JsonPath.read(r.body(), "$.id"));
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).withFailMessage(r.body()).isEqualTo(status);
    assertThat(JsonPath.<String>read(r.body(), "$.code")).isEqualTo(code);
    assertThat(r.body())
        .contains("traceId")
        .doesNotContain("constraint", "SQL", "fixture-", other.toString());
    // instance may echo IDs supplied in the request URI, never hidden resource metadata.
    var publicFields = new HashMap<>(JsonPath.<Map<String, Object>>read(r.body(), "$"));
    var instance = publicFields.remove("instance");
    if (instance != null) {
      assertThat(instance).isEqualTo(r.uri().getPath());
    }
    assertThat(publicFields.toString())
        .doesNotContain(customer.toString(), customerB.toString(), other.toString());
    assertThat(r.headers().firstValue("X-Request-Id")).contains("trip-regression");
  }

  HttpResponse<String> cancel(long version) throws Exception {
    return http(
        "POST",
        base() + "/orders/" + order + "/cancellations",
        "admin",
        "{\"expectedVersion\":" + version + ",\"reason\":\"Before execution\"}");
  }

  long count(String table) throws Exception {
    return (Long) f.db.value("SELECT count(*) FROM " + table + " WHERE tenant_id=?", tenant);
  }

  void orderState(String state) throws Exception {
    f.db.execute(
        "UPDATE orders SET status=?,delivered_at=CASE WHEN ? IN ('DELIVERED','COMPLETED') THEN"
            + " now() ELSE NULL END,completed_at=CASE WHEN ?='COMPLETED' THEN now() ELSE NULL"
            + " END,cancelled_at=CASE WHEN ?='CANCELLED' THEN now() ELSE NULL END WHERE id=?",
        state,
        state,
        state,
        state,
        order);
  }

  @Test
  void migrationHasExactObjectsCatalogsGrantsAndNoCascade() throws Exception {
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
    var expected =
        Map.of(
            "trips",
            Set.of(
                "id",
                "tenant_id",
                "status",
                "current_driver_id",
                "current_tractor_id",
                "planned_initial_trailer_id",
                "started_at",
                "completed_at",
                "cancelled_at",
                "created_at",
                "updated_at",
                "version"),
            "trip_stops",
            Set.of(
                "id",
                "tenant_id",
                "trip_id",
                "sequence_number",
                "stop_type",
                "site_id",
                "location_id",
                "scheduled_at",
                "status",
                "arrived_at",
                "service_started_at",
                "completed_at",
                "departed_at",
                "instructions",
                "site_snapshot",
                "created_at",
                "updated_at",
                "version"),
            "trip_stop_orders",
            Set.of("id", "tenant_id", "trip_stop_id", "order_id", "order_stop_id"));
    for (var entry : expected.entrySet()) {
      try (var c = flyway.getConfiguration().getDataSource().getConnection();
          var q =
              c.prepareStatement(
                  "SELECT column_name FROM information_schema.columns WHERE table_schema='public'"
                      + " AND table_name=?")) {
        q.setString(1, entry.getKey());
        var columns = new HashSet<String>();
        try (var rows = q.executeQuery()) {
          while (rows.next()) {
            columns.add(rows.getString(1));
          }
        }
        assertThat(columns).isEqualTo(entry.getValue());
      }
      assertThat(
              f.db.value(
                  "SELECT relrowsecurity FROM pg_class WHERE oid=?::regclass", entry.getKey()))
          .isEqualTo(true);
      assertThat(
              f.db.value(
                  "SELECT count(*) FROM pg_constraint WHERE conrelid=?::regclass AND contype='f'"
                      + " AND confdeltype='c'",
                  entry.getKey()))
          .isEqualTo(0L);
      for (var privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
        boolean allowed =
            privilege.equals("SELECT")
                || privilege.equals("INSERT")
                || (entry.getKey().equals("trips") && privilege.equals("UPDATE"))
                || (entry.getKey().equals("trip_stop_orders") && privilege.equals("DELETE"));
        assertThat(
                f.db.value(
                    "SELECT has_table_privilege(?, ?, ?)",
                    RUNTIME_USERNAME,
                    entry.getKey(),
                    privilege))
            .isEqualTo(allowed);
      }
      assertThat(
              f.db.value(
                  "SELECT pg_get_userbyid(relowner)=? FROM pg_class WHERE oid=?::regclass",
                  RUNTIME_USERNAME,
                  entry.getKey()))
          .isEqualTo(false);
      assertThat(
              f.db.value(
                  "SELECT count(*) FROM information_schema.table_privileges WHERE table_name=? AND"
                      + " grantee='PUBLIC'",
                  entry.getKey()))
          .isEqualTo(0L);
    }
    assertThat(
            f.db.value(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=?", RUNTIME_USERNAME))
        .isEqualTo(false);
    assertThat(f.db.value("SELECT has_schema_privilege(?,'public','CREATE')", RUNTIME_USERNAME))
        .isEqualTo(false);
    for (var table : List.of("trip_assignment_events", "trip_stop_events", "trailer_couplings")) {
      assertThat(f.db.value("SELECT to_regclass(?)", table)).isNull();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"none", "driver", "tractor", "both"})
  void initialAssignmentPolicyAndMilestones(String mode) throws Exception {
    var b = plan();
    if (mode.equals("driver") || mode.equals("both")) {
      b.put("driverId", driver.toString());
    }
    if (mode.equals("tractor") || mode.equals("both")) {
      b.put("tractorId", tractor.toString());
    }
    b.put("plannedInitialTrailerId", trailer.toString());
    var physical = f.db.value("SELECT to_jsonb(t)::text FROM trailers t WHERE id=?", trailer);
    var r = post(b);
    var trip = id(r);
    assertThat(JsonPath.<String>read(r.body(), "$.status"))
        .isEqualTo(mode.equals("both") ? "ASSIGNED" : "PLANNED");
    assertThat(JsonPath.<Number>read(r.body(), "$.version").longValue()).isZero();
    for (var field : List.of("startedAt", "completedAt", "cancelledAt")) {
      assertThat(JsonPath.<Object>read(r.body(), "$." + field)).isNull();
    }
    assertThat(JsonPath.<List<?>>read(r.body(), "$.stops")).hasSize(1);
    assertThat(JsonPath.<String>read(r.body(), "$.stops[0].status")).isEqualTo("PENDING");
    assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("PLANNED");
    assertThat(f.db.value("SELECT version FROM orders WHERE id=?", order)).isEqualTo(1L);
    assertThat(f.db.value("SELECT to_jsonb(t)::text FROM trailers t WHERE id=?", trailer))
        .isEqualTo(physical);
    assertThat(r.body()).doesNotContain("tenantId", "orderStopId", "assignmentHistory");
    assertThat(f.db.value("SELECT order_stop_id FROM trip_stop_orders WHERE tenant_id=?", tenant))
        .isNull();
    assertThat(f.db.value("SELECT id FROM trips WHERE id=?", trip)).isEqualTo(trip);
  }

  @Test
  void openTransitionOccursOnceAndMultipleTripsAndOrdersRemainIndependent() throws Exception {
    var b = plan();
    b.put("orderIds", List.of(orderB.toString(), order.toString()));
    b.put(
        "stops",
        List.of(stop(7, List.of(order, orderB)), stop(4, List.of(order)), stop(9, List.of())));
    var first = service.create(admin, tenant, b);
    assertThat(first.stops())
        .extracting(TripContracts.Stop::sequenceNumber)
        .containsExactly(4, 7, 9);
    assertThat(first.stops().getLast().orderIds()).isEmpty();
    var second = service.create(admin, tenant, plan());
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(count("trip_stop_orders")).isEqualTo(4);
    assertThat(f.db.value("SELECT version FROM orders WHERE id=?", order)).isEqualTo(1L);
    var planned =
        events.stream(OrderLifecycleEvent.class)
            .filter(e -> e.action() == OrderLifecycleEvent.Action.ORDER_PLANNED)
            .toList();
    assertThat(planned).hasSize(2);
    assertThat(planned)
        .allSatisfy(
            e -> {
              assertThat(e.actor().userId()).isEqualTo(user);
              assertThat(e.actor().membershipId()).isEqualTo(membership);
              assertThat(e.previousState()).isEqualTo("OPEN");
            });
    assertThat(first.stops()).noneMatch(s -> s.stopType().equals("BASE_RETURN"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "IN_PROGRESS",
        "PARTIALLY_DELIVERED",
        "DELIVERED",
        "COMPLETED",
        "CANCELLING",
        "CANCELLED"
      })
  void invalidOrderStateRollsBackWholeMultiOrderPlan(String state) throws Exception {
    orderState(state);
    var b = plan();
    b.put("orderIds", List.of(order.toString(), orderB.toString()));
    b.put("stops", List.of(stop(1, List.of(order, orderB))));
    problem(post(b), 409, "INVALID_STATE_TRANSITION");
    for (var table : List.of("trips", "trip_stops", "trip_stop_orders")) {
      assertThat(count(table)).isZero();
    }
    assertThat(f.db.value("SELECT status FROM orders WHERE id=?", orderB)).isEqualTo("OPEN");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "driver",
        "tractor",
        "trailerInactive",
        "trailerOos",
        "foreignDriver",
        "foreignTractor",
        "foreignTrailer"
      })
  void unavailableInitialEquipmentNeverDegradesOrCreatesPartialRows(String kind) throws Exception {
    var b = plan();
    switch (kind) {
      case "driver" -> {
        f.db.execute("UPDATE drivers SET active=false WHERE id=?", driver);
        b.put("driverId", driver.toString());
      }
      case "tractor" -> {
        f.db.execute("UPDATE tractors SET active=false WHERE id=?", tractor);
        b.put("tractorId", tractor.toString());
      }
      case "trailerInactive" -> {
        f.db.execute("UPDATE trailers SET active=false WHERE id=?", trailer);
        b.put("plannedInitialTrailerId", trailer.toString());
      }
      case "trailerOos" -> {
        f.db.execute(
            "UPDATE trailers SET serviceability_state='OUT_OF_SERVICE' WHERE id=?", trailer);
        b.put("plannedInitialTrailerId", trailer.toString());
      }
      case "foreignDriver" -> b.put("driverId", f.db.driver(other, null).toString());
      case "foreignTractor" -> b.put("tractorId", f.db.tractor(other, "Foreign").toString());
      case "foreignTrailer" ->
          b.put(
              "plannedInitialTrailerId",
              new TrailerFixtures(flyway).trailer(other, "Foreign", null).toString());
    }
    problem(
        post(b),
        kind.startsWith("foreign") ? 404 : 409,
        kind.startsWith("foreign") ? "RESOURCE_NOT_FOUND" : "RESOURCE_CONFLICT");
    assertThat(count("trips")).isZero();
    assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("OPEN");
  }

  @Test
  void authIsFreshTenantWideAndDoesNotAcceptCustomerPlannerOrClaims() throws Exception {
    problem(http("POST", base() + "/trips", null, json(plan())), 401, "AUTHENTICATION_REQUIRED");
    for (var token : List.of("dispatcher", "yard", "driver")) {
      problem(http("POST", base() + "/trips", token, json(plan())), 403, "CAPABILITY_DENIED");
      problem(http("GET", base() + "/trips", token, ""), 403, "CAPABILITY_DENIED");
    }
    id(http("POST", base() + "/trips", "ops", json(plan())));
    f.db.execute("UPDATE tenant_memberships SET status='SUSPENDED' WHERE id=?", membership);
    problem(post(plan()), 403, "TENANT_ACCESS_DENIED");
    f.db.execute("UPDATE tenant_memberships SET status='ACTIVE' WHERE id=?", membership);
    f.db.execute("DELETE FROM role_assignments WHERE membership_id=?", membership);
    problem(post(plan()), 403, "CAPABILITY_DENIED");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "neutral",
        "shared",
        "own",
        "tripOnly",
        "tripOnlyCustomer",
        "multiCustomerSpecific",
        "wrongSite",
        "foreignSite",
        "foreignLocation",
        "snapshot"
      })
  void destinationCompatibilityAndFrozenSnapshots(String kind) throws Exception {
    var b = plan();
    var s = stop(1, List.of(order));
    var orders = List.of(order);
    s.put("siteId", site.toString());
    s.put("locationId", neutral.toString());
    switch (kind) {
      case "shared" -> {
        orders = List.of(order, orderB);
        s.put("orderIds", orders.stream().map(UUID::toString).toList());
      }
      case "own" -> s.put("locationId", f.location(tenant, site, customer).toString());
      case "tripOnly", "tripOnlyCustomer" -> {
        var operational = stop(6, List.of());
        operational.put("stopType", "BASE_RETURN");
        operational.put("siteId", site.toString());
        operational.put(
            "locationId",
            kind.equals("tripOnly")
                ? neutral.toString()
                : f.location(tenant, site, customer).toString());
        b.put("stops", List.of(s, operational));
      }
      case "multiCustomerSpecific" -> {
        orders = List.of(order, orderB);
        s.put("orderIds", orders.stream().map(UUID::toString).toList());
        s.put("locationId", f.location(tenant, site, customer).toString());
      }
      case "wrongSite" ->
          s.put("locationId", f.location(tenant, f.site(tenant, customer), null).toString());
      case "foreignSite" -> {
        var foreignSite = f.site(other, null);
        s.put("siteId", foreignSite.toString());
        s.remove("locationId");
      }
      case "foreignLocation" ->
          s.put("locationId", f.location(other, f.site(other, null), null).toString());
      case "snapshot" -> {
        s.remove("siteId");
        s.remove("locationId");
      }
    }
    b.put("orderIds", orders.stream().map(UUID::toString).toList());
    if (!kind.startsWith("tripOnly")) {
      b.put("stops", List.of(s));
    }
    var r = post(b);
    if (Set.of(
            "tripOnlyCustomer",
            "multiCustomerSpecific",
            "wrongSite",
            "foreignSite",
            "foreignLocation")
        .contains(kind)) {
      problem(r, 409, "RESOURCE_CONFLICT");
      assertThat(count("trips")).isZero();
      return;
    }
    var trip = id(r);
    assertThat(JsonPath.<String>read(r.body(), "$.stops[0].siteSnapshot.name"))
        .isEqualTo("Frozen executable");
    f.db.execute("UPDATE physical_sites SET name='Changed master' WHERE id=?", site);
    var detail = http("GET", base() + "/trips/" + trip, "admin", "");
    assertThat(detail.statusCode()).isEqualTo(200);
    assertThat(detail.body()).contains("Frozen executable").doesNotContain("Changed master");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "empty",
        "unknown",
        "invalidTimezone",
        "invalidLatitude",
        "invalidLongitude",
        "invalidRadius"
      })
  void snapshotUsesExactSharedOrderValidation(String mode) throws Exception {
    var b = plan();
    var s = stop(1, List.of(order));
    var snapshot = new HashMap<String, Object>(Map.of("name", "Frozen"));
    switch (mode) {
      case "empty" -> snapshot.clear();
      case "unknown" -> snapshot.put("customerId", customer.toString());
      case "invalidTimezone" -> snapshot.put("timezone", "Mars/Base");
      case "invalidLatitude" -> snapshot.put("latitude", 91);
      case "invalidLongitude" -> snapshot.put("longitude", 181);
      case "invalidRadius" -> snapshot.put("geofenceRadiusM", 0);
    }
    s.put("siteSnapshot", snapshot);
    b.put("stops", List.of(s));
    problem(post(b), 400, "VALIDATION_FAILED");
    assertThat(count("trips")).isZero();
  }

  @Test
  void listUsesRealKeysetAndSupportedFiltersAndOrderUnplannedIgnoresManifests() throws Exception {
    var unplanned = f.order(tenant, customer, user);
    var foreignOrder = f.order(tenant, customerB, user);
    var terminal = f.order(tenant, customer, user);
    f.db.execute("UPDATE orders SET status='CANCELLED',cancelled_at=now() WHERE id=?", terminal);
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
        unplanned);
    var b = plan();
    b.put("driverId", driver.toString());
    var first = id(post(b));
    var second = id(post(plan()));
    var r = http("GET", base() + "/trips?limit=1", "admin", "");
    assertThat(r.statusCode()).isEqualTo(200);
    var cursor = JsonPath.<String>read(r.body(), "$.nextCursor");
    assertThat(cursor).isNotBlank();
    var r2 = http("GET", base() + "/trips?limit=1&cursor=" + cursor, "admin", "");
    assertThat(JsonPath.<String>read(r.body(), "$.items[0].id"))
        .isNotEqualTo(JsonPath.<String>read(r2.body(), "$.items[0].id"));
    assertThat(JsonPath.<Object>read(r2.body(), "$.nextCursor")).isNull();
    r =
        http(
            "GET",
            base() + "/trips?driverId=" + driver + "&status=PLANNED&customerId=" + customer,
            "admin",
            "");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.items[*].id"))
        .containsExactly(first.toString());
    for (var query :
        List.of(
            "active=true",
            "from=2026-01-01",
            "to=2026-01-01",
            "limit=201",
            "status=WRONG",
            "driverId=bad",
            "cursor=bad")) {
      problem(http("GET", base() + "/trips?" + query, "admin", ""), 400, "VALIDATION_FAILED");
    }
    r = http("GET", base() + "/orders?unplanned=true", "admin", "");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.items[*].id"))
        .contains(unplanned.toString(), foreignOrder.toString())
        .doesNotContain(order.toString(), terminal.toString());
    f.db.execute("UPDATE trips SET status='COMPLETED' WHERE id IN (?,?)", first, second);
    r = http("GET", base() + "/orders?unplanned=true&customerId=" + customer, "admin", "");
    assertThat(JsonPath.<List<String>>read(r.body(), "$.items[*].id"))
        .contains(order.toString(), unplanned.toString())
        .doesNotContain(foreignOrder.toString(), terminal.toString());
    problem(http("GET", base() + "/orders?unplanned=maybe", "admin", ""), 400, "VALIDATION_FAILED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "ASSIGNED"})
  void cancellationReleasesOnlyItsMappingsAndPreservesTripStopsAndOtherOrders(String status)
      throws Exception {
    var b = plan();
    b.put("orderIds", List.of(order.toString(), orderB.toString()));
    b.put("stops", List.of(stop(1, List.of(order, orderB)), stop(3, List.of(order))));
    if (status.equals("ASSIGNED")) {
      b.put("driverId", driver.toString());
      b.put("tractorId", tractor.toString());
    }
    var trip = id(post(b));
    var stops =
        f.db.value(
            "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM trip_stops s WHERE trip_id=?",
            trip);
    var orderStops =
        f.db.value(
            "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM order_stops s WHERE order_id=?",
            order);
    assertThat(cancel(1).statusCode()).isEqualTo(200);
    assertThat(f.db.value("SELECT count(*) FROM trip_stop_orders WHERE order_id=?", order))
        .isEqualTo(0L);
    assertThat(f.db.value("SELECT count(*) FROM trip_stop_orders WHERE order_id=?", orderB))
        .isEqualTo(1L);
    assertThat(f.db.value("SELECT status FROM trips WHERE id=?", trip)).isEqualTo(status);
    assertThat(f.db.value("SELECT version FROM trips WHERE id=?", trip)).isEqualTo(1L);
    assertThat(
            f.db.value(
                "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM trip_stops s WHERE trip_id=?",
                trip))
        .isEqualTo(stops);
    assertThat(
            f.db.value(
                "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM order_stops s WHERE"
                    + " order_id=?",
                order))
        .isEqualTo(orderStops);
  }

  @ParameterizedTest
  @ValueSource(strings = {"IN_TRANSIT", "AT_STOP", "CANCELLING", "COMPLETED", "CANCELLED"})
  void cancellationCannotDestructivelyDetachExecutedOrHistoricalTrips(String status)
      throws Exception {
    var trip = id(post(plan()));
    f.db.execute("UPDATE trips SET status=? WHERE id=?", status, trip);
    problem(cancel(1), 409, "RESOURCE_CONFLICT");
    assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("PLANNED");
    assertThat(count("trip_stop_orders")).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"trips", "trip_stops", "trip_stop_orders", "orders"})
  void anyCreateFailureRollsBackTripsStopsMappingsAndOrderTransition(String table)
      throws Exception {
    String name = "fail_trip_" + UUID.randomUUID().toString().replace("-", "");
    var operation = table.equals("orders") ? "UPDATE" : "INSERT";
    f.db.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id='"
            + tenant
            + "' THEN RAISE EXCEPTION 'controlled failure' USING ERRCODE='08006'; END IF; RETURN"
            + " NEW; END $$");
    f.db.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE "
            + operation
            + " ON "
            + table
            + " FOR EACH ROW EXECUTE FUNCTION "
            + name
            + "()");
    try {
      var r = post(plan());
      assertThat(r.statusCode()).isEqualTo(500);
      assertThat(r.body()).contains("traceId").doesNotContain(name, "controlled failure", "08006");
      for (var child : List.of("trips", "trip_stops", "trip_stop_orders")) {
        assertThat(count(child)).isZero();
      }
      assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("OPEN");
      assertThat(f.db.value("SELECT version FROM orders WHERE id=?", order)).isEqualTo(0L);
    } finally {
      f.db.execute("DROP TRIGGER " + name + " ON " + table);
      f.db.execute("DROP FUNCTION " + name + "()");
    }
  }

  @Test
  void mappingReleaseFailureRollsBackCancellationAndManifestRelease() throws Exception {
    var trip = id(post(plan()));
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
        order);
    String name = "fail_release_" + UUID.randomUUID().toString().replace("-", "");
    f.db.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.tenant_id='"
            + tenant
            + "' THEN RAISE EXCEPTION 'controlled release failure' USING ERRCODE='08006'; END IF;"
            + " RETURN OLD; END $$");
    f.db.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE DELETE ON trip_stop_orders FOR EACH ROW EXECUTE FUNCTION "
            + name
            + "()");
    try {
      assertThat(cancel(1).statusCode()).isEqualTo(500);
      assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("PLANNED");
      assertThat(f.db.value("SELECT version FROM orders WHERE id=?", order)).isEqualTo(1L);
      assertThat(count("trip_stop_orders")).isEqualTo(1);
      assertThat(count("manifest_orders")).isEqualTo(1);
      assertThat(f.db.value("SELECT version FROM load_manifests WHERE id=?", manifest))
          .isEqualTo(0L);
      assertThat(f.db.value("SELECT version FROM trips WHERE id=?", trip)).isEqualTo(0L);
    } finally {
      f.db.execute("DROP TRIGGER " + name + " ON trip_stop_orders");
      f.db.execute("DROP FUNCTION " + name + "()");
    }
  }

  void waitFor(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test transaction timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  void lockWait(String table) {
    await()
        .atMost(Duration.ofSeconds(12))
        .untilAsserted(
            () ->
                assertThat(
                        (Long)
                            f.db.value(
                                "SELECT count(*) FROM pg_stat_activity WHERE usename=? AND"
                                    + " wait_event_type='Lock' AND query LIKE ?",
                                RUNTIME_USERNAME,
                                "%" + table + "%"))
                    .isGreaterThan(0));
  }

  @Test
  void planningFirstMakesCancellationWaitThenReleaseItsCommittedMapping() throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var creation =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var result = service.create(admin, tenant, plan());
                        held.countDown();
                        waitFor(release);
                        return result;
                      }));
      assertThat(held.await(12, TimeUnit.SECONDS)).isTrue();
      var cancellation = pool.submit(() -> cancel(1));
      try {
        lockWait("orders");
      } finally {
        release.countDown();
      }
      var trip = creation.get(25, TimeUnit.SECONDS);
      assertThat(cancellation.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      assertThat(count("trip_stop_orders")).isZero();
      assertThat(count("trip_stops")).isEqualTo(1);
      assertThat(f.db.value("SELECT version FROM trips WHERE id=?", trip.id())).isEqualTo(1L);
      assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("CANCELLED");
    } finally {
      release.countDown();
    }
  }

  @Test
  void cancellationFirstMakesPlanningWaitThenRejectCancelledOrder() throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              held.countDown();
              waitFor(release);
              return call.callRealMethod();
            })
        .when(
            org.springframework.test.util.AopTestUtils.<TripPlanningRelease>getUltimateTargetObject(
                releaseHandler))
        .release(tenant, order);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var cancellation = pool.submit(() -> cancel(0));
      assertThat(held.await(12, TimeUnit.SECONDS)).isTrue();
      var planning = pool.submit(() -> post(plan()));
      try {
        lockWait("orders");
      } finally {
        release.countDown();
      }
      assertThat(cancellation.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      problem(planning.get(25, TimeUnit.SECONDS), 409, "INVALID_STATE_TRANSITION");
      assertThat(count("trips")).isZero();
      assertThat(count("trip_stops")).isZero();
      assertThat(count("trip_stop_orders")).isZero();
    } finally {
      release.countDown();
      reset(releaseHandler);
    }
  }

  @Test
  void twoConcurrentPlannersSerializeOneOpenTransitionAndCreateTwoTrips() throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var result = service.create(admin, tenant, plan());
                        held.countDown();
                        waitFor(release);
                        return result;
                      }));
      assertThat(held.await(12, TimeUnit.SECONDS)).isTrue();
      var second = pool.submit(() -> post(plan()));
      try {
        lockWait("orders");
      } finally {
        release.countDown();
      }
      first.get(25, TimeUnit.SECONDS);
      id(second.get(25, TimeUnit.SECONDS));
      assertThat(count("trips")).isEqualTo(2);
      assertThat(count("trip_stop_orders")).isEqualTo(2);
      assertThat(f.db.value("SELECT version FROM orders WHERE id=?", order)).isEqualTo(1L);
    } finally {
      release.countDown();
    }
  }

  String association() {
    return base() + "/customers/" + customer + "/sites/" + site;
  }

  Map<String, Object> normalizedPlan() {
    var b = plan();
    var s = stop(1, List.of(order));
    s.put("siteId", site.toString());
    s.put("locationId", neutral.toString());
    b.put("stops", List.of(s));
    return b;
  }

  @Test
  void siteCreateFirstMakesAssociationRemovalWaitAndDetectActiveTripUse() throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var planning =
          pool.submit(
              () ->
                  transactions.execute(
                      tenant,
                      () -> {
                        var result = service.create(admin, tenant, normalizedPlan());
                        held.countDown();
                        waitFor(release);
                        return result;
                      }));
      assertThat(held.await(12, TimeUnit.SECONDS)).isTrue();
      var removal = pool.submit(() -> http("DELETE", association(), "admin", ""));
      try {
        lockWait("physical_sites");
      } finally {
        release.countDown();
      }
      planning.get(25, TimeUnit.SECONDS);
      problem(removal.get(25, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
    } finally {
      release.countDown();
    }
  }

  @Test
  void siteRemovalFirstMakesPlanningWaitThenRejectMissingAssociation() throws Exception {
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              held.countDown();
              waitFor(release);
              return call.callRealMethod();
            })
        .when(
            org.springframework.test.util.AopTestUtils.<TripSiteUsageGuard>getUltimateTargetObject(
                usage))
        .hasActiveUse(tenant, customer, site);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var removal = pool.submit(() -> http("DELETE", association(), "admin", ""));
      assertThat(held.await(12, TimeUnit.SECONDS)).isTrue();
      var planning = pool.submit(() -> post(normalizedPlan()));
      try {
        lockWait("physical_sites");
      } finally {
        release.countDown();
      }
      assertThat(removal.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(204);
      problem(planning.get(25, TimeUnit.SECONDS), 409, "RESOURCE_CONFLICT");
      assertThat(count("trips")).isZero();
      assertThat(f.db.value("SELECT status FROM orders WHERE id=?", order)).isEqualTo("OPEN");
    } finally {
      release.countDown();
      reset(usage);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PLANNED",
        "ASSIGNED",
        "IN_TRANSIT",
        "AT_STOP",
        "CANCELLING",
        "COMPLETED",
        "CANCELLED"
      })
  void siteGuardProtectsActiveTripUseButTerminalHistorySurvivesRemoval(String status)
      throws Exception {
    var trip = id(post(normalizedPlan()));
    f.db.execute("UPDATE trips SET status=? WHERE id=?", status, trip);
    f.db.execute(
        "UPDATE orders SET status='COMPLETED',delivered_at=now(),completed_at=now() WHERE id=?",
        order);
    var before = f.db.value("SELECT to_jsonb(s)::text FROM trip_stops s WHERE trip_id=?", trip);
    var r = http("DELETE", association(), "admin", "");
    if (status.equals("COMPLETED") || status.equals("CANCELLED")) {
      assertThat(r.statusCode()).isEqualTo(204);
      assertThat(f.db.value("SELECT to_jsonb(s)::text FROM trip_stops s WHERE trip_id=?", trip))
          .isEqualTo(before);
    } else {
      problem(r, 409, "RESOURCE_CONFLICT");
    }
  }

  UUID rawTrip(UUID owner) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO trips(id,tenant_id,status,created_at,updated_at)"
            + " VALUES(?,?,'PLANNED',now(),now())",
        id,
        owner);
    return id;
  }

  UUID rawStop(UUID owner, UUID trip, int sequence) throws Exception {
    var id = UUID.randomUUID();
    f.db.execute(
        "INSERT INTO"
            + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at)"
            + " VALUES(?,?,?,?,'DELIVERY','PENDING','{\"name\":\"Frozen\"}',now(),now())",
        id,
        owner,
        trip,
        sequence);
    return id;
  }

  void mapping(UUID owner, UUID stop, UUID orderId, UUID requested) throws Exception {
    f.db.execute(
        "INSERT INTO trip_stop_orders VALUES(?,?,?,?,?)",
        UUID.randomUUID(),
        owner,
        stop,
        orderId,
        requested);
  }

  void denied(String state, String sql, Object... values) {
    assertThatThrownBy(() -> f.db.execute(sql, values))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(state));
  }

  @Test
  void nullSafeMappingCardinalityAllowsSpecificStopsAndManyToManyButRejectsDuplicates()
      throws Exception {
    var one = rawTrip(tenant);
    var two = rawTrip(tenant);
    var stop = rawStop(tenant, one, 1);
    var another = rawStop(tenant, one, 8);
    var third = rawStop(tenant, two, 1);
    mapping(tenant, stop, order, null);
    mapping(tenant, stop, orderB, null);
    mapping(tenant, another, order, null);
    mapping(tenant, third, order, null);
    assertThatThrownBy(() -> mapping(tenant, stop, order, null))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
    var requested = f.stop(tenant, order, 5, null, null, "{\"name\":\"Specific A\"}");
    var requested2 = f.stop(tenant, order, 6, null, null, "{\"name\":\"Specific B\"}");
    mapping(tenant, stop, order, requested);
    mapping(tenant, stop, order, requested2);
    assertThatThrownBy(() -> mapping(tenant, stop, order, requested))
        .isInstanceOfSatisfying(
            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
    assertThat(count("trip_stop_orders")).isEqualTo(6);
    assertThat(
            f.db.value(
                "SELECT indnullsnotdistinct FROM pg_index WHERE indexrelid=(SELECT conindid FROM"
                    + " pg_constraint WHERE conrelid='trip_stop_orders'::regclass AND"
                    + " contype='u')"))
        .isEqualTo(true);
    denied(
        "23505",
        "INSERT INTO"
            + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at)"
            + " VALUES(?,?,?,1,'OTHER','PENDING','{\"name\":\"Other\"}',now(),now())",
        UUID.randomUUID(),
        tenant,
        one);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "driver",
        "tractor",
        "trailer",
        "trip",
        "site",
        "locationSite",
        "locationTenant",
        "mappingStop",
        "mappingOrder",
        "requestedOrder"
      })
  void databaseRejectsEveryCrossTenantOrMismatchedParent(String kind) throws Exception {
    var trip = rawTrip(tenant);
    var stop = rawStop(tenant, trip, 1);
    var foreignTrip = rawTrip(other);
    var foreignStop = rawStop(other, foreignTrip, 1);
    var foreignOrder = f.order(other, f.customer(other), user);
    switch (kind) {
      case "driver" ->
          denied(
              "23503",
              "UPDATE trips SET current_driver_id=? WHERE id=?",
              f.db.driver(other, null),
              trip);
      case "tractor" ->
          denied(
              "23503",
              "UPDATE trips SET current_tractor_id=? WHERE id=?",
              f.db.tractor(other, "Other"),
              trip);
      case "trailer" ->
          denied(
              "23503",
              "UPDATE trips SET planned_initial_trailer_id=? WHERE id=?",
              new TrailerFixtures(flyway).trailer(other, "Other", null),
              trip);
      case "trip" ->
          denied("23503", "UPDATE trip_stops SET trip_id=? WHERE id=?", foreignTrip, stop);
      case "site" ->
          denied("23503", "UPDATE trip_stops SET site_id=? WHERE id=?", f.site(other, null), stop);
      case "locationSite" ->
          denied(
              "23503",
              "UPDATE trip_stops SET site_id=?,location_id=? WHERE id=?",
              site,
              f.location(tenant, f.site(tenant, customer), null),
              stop);
      case "locationTenant" ->
          denied(
              "23503",
              "UPDATE trip_stops SET site_id=?,location_id=? WHERE id=?",
              site,
              f.location(other, f.site(other, null), null),
              stop);
      case "mappingStop" ->
          assertThatThrownBy(() -> mapping(tenant, foreignStop, order, null))
              .isInstanceOfSatisfying(
                  SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
      case "mappingOrder" ->
          assertThatThrownBy(() -> mapping(tenant, stop, foreignOrder, null))
              .isInstanceOfSatisfying(
                  SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
      case "requestedOrder" -> {
        var requested = f.stop(tenant, orderB, 5, null, null, "{\"name\":\"Other Order\"}");
        assertThatThrownBy(() -> mapping(tenant, stop, order, requested))
            .isInstanceOfSatisfying(
                SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23503"));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sequenceZero",
        "noDestination",
        "locationWithoutSite",
        "invalidTrip",
        "invalidStop",
        "fuel"
      })
  void databaseChecksAreFinalBarrierEvenIfServiceBypassed(String kind) throws Exception {
    var trip = rawTrip(tenant);
    var stop = rawStop(tenant, trip, 1);
    switch (kind) {
      case "sequenceZero" ->
          denied("23514", "UPDATE trip_stops SET sequence_number=0 WHERE id=?", stop);
      case "noDestination" ->
          denied("23514", "UPDATE trip_stops SET site_snapshot=NULL WHERE id=?", stop);
      case "locationWithoutSite" ->
          denied("23514", "UPDATE trip_stops SET location_id=? WHERE id=?", neutral, stop);
      case "invalidTrip" -> denied("23514", "UPDATE trips SET status='WRONG' WHERE id=?", trip);
      case "invalidStop" ->
          denied("23514", "UPDATE trip_stops SET status='WRONG' WHERE id=?", stop);
      case "fuel" -> denied("23514", "UPDATE trip_stops SET stop_type='FUEL' WHERE id=?", stop);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PLANNED",
        "ASSIGNED",
        "IN_TRANSIT",
        "AT_STOP",
        "CANCELLING",
        "COMPLETED",
        "CANCELLED"
      })
  void fullAcceptedTripCatalogRemainsReadyForLaterIssues(String status) throws Exception {
    var trip = rawTrip(tenant);
    f.db.execute("UPDATE trips SET status=? WHERE id=?", status, trip);
    assertThat(f.db.value("SELECT status FROM trips WHERE id=?", trip)).isEqualTo(status);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PENDING",
        "EN_ROUTE",
        "ARRIVED",
        "IN_SERVICE",
        "EXCEPTION",
        "COMPLETED",
        "CANCELLED"
      })
  void fullAcceptedStopCatalogRemainsReadyForLaterIssues(String status) throws Exception {
    var stop = rawStop(tenant, rawTrip(tenant), 1);
    f.db.execute("UPDATE trip_stops SET status=? WHERE id=?", status, stop);
    assertThat(f.db.value("SELECT status FROM trip_stops WHERE id=?", stop)).isEqualTo(status);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "BASE_DEPARTURE",
        "PICKUP",
        "DELIVERY",
        "LIVE_LOAD",
        "LIVE_UNLOAD",
        "DROP_HOOK",
        "BASE_RETURN",
        "OTHER"
      })
  void acceptedStopTypes(String type) throws Exception {
    var stop = rawStop(tenant, rawTrip(tenant), 1);
    f.db.execute("UPDATE trip_stops SET stop_type=? WHERE id=?", type, stop);
    assertThat(f.db.value("SELECT stop_type FROM trip_stops WHERE id=?", stop)).isEqualTo(type);
  }

  void context(Connection c, UUID tenantId, UUID userId) throws Exception {
    try (var q =
        c.prepareStatement(
            "SELECT"
                + " set_config('app.current_tenant_id',?,true),set_config('app.current_user_id',?,true)")) {
      q.setString(1, tenantId == null ? "" : tenantId.toString());
      q.setString(2, userId == null ? "" : userId.toString());
      q.executeQuery().close();
    }
  }

  long runtimeCount(Connection c, String table) throws Exception {
    try (var q = c.createStatement();
        var r = q.executeQuery("SELECT count(*) FROM " + table)) {
      r.next();
      return r.getLong(1);
    }
  }

  @Test
  void tenantRlsRequiresPureTenantAndBlocksCrossTenantWritesForAllThreeTables() throws Exception {
    var trip = rawTrip(tenant);
    var stop = rawStop(tenant, trip, 1);
    mapping(tenant, stop, order, null);
    var foreignTrip = rawTrip(other);
    var foreignStop = rawStop(other, foreignTrip, 1);
    mapping(other, foreignStop, f.order(other, f.customer(other), user), null);
    try (var c = runtime.getConnection()) {
      c.setAutoCommit(false);
      context(c, null, null);
      for (var table : List.of("trips", "trip_stops", "trip_stop_orders")) {
        assertThat(runtimeCount(c, table)).isZero();
      }
      context(c, tenant, user);
      for (var table : List.of("trips", "trip_stops", "trip_stop_orders")) {
        assertThat(runtimeCount(c, table)).isZero();
      }
      context(c, tenant, null);
      for (var table : List.of("trips", "trip_stops", "trip_stop_orders")) {
        assertThat(runtimeCount(c, table)).isEqualTo(1);
      }
      assertThat(f.db.execute(c, "UPDATE trips SET status='ASSIGNED' WHERE id=?", foreignTrip))
          .isZero();
      c.rollback();
    }
    for (var table : List.of("trips", "trip_stops", "trip_stop_orders")) {
      try (var c = runtime.getConnection()) {
        c.setAutoCommit(false);
        context(c, tenant, null);
        String sql =
            switch (table) {
              case "trips" ->
                  "INSERT INTO trips(id,tenant_id,status,created_at,updated_at)"
                      + " VALUES(?,?,'PLANNED',now(),now())";
              case "trip_stops" ->
                  "INSERT INTO"
                      + " trip_stops(id,tenant_id,trip_id,sequence_number,stop_type,status,site_snapshot,created_at,updated_at)"
                      + " VALUES(?,?,?,9,'OTHER','PENDING','{\"name\":\"Foreign\"}',now(),now())";
              default ->
                  "INSERT INTO trip_stop_orders(id,tenant_id,trip_stop_id,order_id)"
                      + " VALUES(?,?,?,?)";
            };
        Object[] values =
            table.equals("trips")
                ? new Object[] {UUID.randomUUID(), other}
                : new Object[] {
                  UUID.randomUUID(),
                  other,
                  table.equals("trip_stops") ? foreignTrip : foreignStop,
                  order
                };
        if (table.equals("trip_stops")) {
          values = new Object[] {UUID.randomUUID(), other, foreignTrip};
        }
        final var parameters = values;
        assertThatThrownBy(() -> f.db.execute(c, sql, parameters))
            .isInstanceOfSatisfying(
                SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
        c.rollback();
      }
    }
    try (var c = runtime.getConnection()) {
      c.setAutoCommit(false);
      context(c, tenant, null);
      assertThatThrownBy(
              () -> f.db.execute(c, "UPDATE trips SET tenant_id=? WHERE id=?", other, trip))
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
      c.rollback();
    }
  }

  @Test
  void unplannedReadsRetainFreshCustomerScopeAndDirectTripIdsStayTenantHidden() throws Exception {
    var dispatcher =
        (UUID)
            f.db.value(
                "SELECT id FROM users WHERE firebase_uid=?", verifier.verify("dispatcher").uid());
    f.db.execute(
        "UPDATE role_assignments SET customer_id=? WHERE tenant_id=? AND membership_id IN (SELECT"
            + " id FROM tenant_memberships WHERE tenant_id=? AND user_id=?)",
        customer,
        tenant,
        tenant,
        dispatcher);
    var response = http("GET", base() + "/orders?unplanned=true", "dispatcher", "");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<List<String>>read(response.body(), "$.items[*].id"))
        .containsExactly(order.toString());
    assertThat(response.body()).doesNotContain(orderB.toString(), customerB.toString());
    var foreign = rawTrip(other);
    problem(http("GET", base() + "/trips/" + foreign, "admin", ""), 404, "RESOURCE_NOT_FOUND");
  }

  @Test
  void failedSecondOrderTransitionDoesNotCommitFirstPlanningHookOrAnyTripRows() throws Exception {
    var b = plan();
    b.put("orderIds", List.of(order.toString(), orderB.toString()));
    b.put("stops", List.of(stop(1, List.of(order, orderB))));
    // The policy locks and transitions in one consistent UUID order.
    var second = List.of(order, orderB).stream().sorted().toList().getLast();
    String name = "fail_transition_" + UUID.randomUUID().toString().replace("-", "");
    f.db.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"
            + second
            + "' AND NEW.status='PLANNED' THEN RAISE EXCEPTION 'controlled failure' USING"
            + " ERRCODE='08006'; END IF; RETURN NEW; END $$");
    f.db.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION "
            + name
            + "()");
    try {
      assertThat(post(b).statusCode()).isEqualTo(500);
      assertThat(count("trips")).isZero();
      assertThat(count("trip_stops")).isZero();
      assertThat(count("trip_stop_orders")).isZero();
      for (var id : List.of(order, orderB)) {
        assertThat(f.db.value("SELECT status FROM orders WHERE id=?", id)).isEqualTo("OPEN");
        assertThat(f.db.value("SELECT version FROM orders WHERE id=?", id)).isEqualTo(0L);
      }
      assertThat(committed.rows.stream().filter(e -> e.tenantId().equals(tenant))).isEmpty();
    } finally {
      f.db.execute("DROP TRIGGER " + name + " ON orders");
      f.db.execute("DROP FUNCTION " + name + "()");
    }
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class EventConfiguration {
    @org.springframework.context.annotation.Bean
    CommittedEvents committedEvents() {
      return new CommittedEvents();
    }
  }

  static class CommittedEvents {
    final java.util.concurrent.ConcurrentLinkedQueue<OrderLifecycleEvent> rows =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    @org.springframework.transaction.event.TransactionalEventListener(
        phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    void record(OrderLifecycleEvent event) {
      rows.add(event);
    }
  }
}
