package com.trailersaas.trailers.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import com.trailersaas.*;
import com.trailersaas.identity.*;
import jakarta.persistence.EntityManagerFactory;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
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
class TrailerReadPostgresTests extends PostgresIntegrationTest {
  static final String TOKEN = "synthetic-trailer-read-bearer";
  static final Instant TIME = Instant.parse("2026-10-01T12:00:00Z");
  @Autowired Flyway flyway;
  @Autowired TrailerReadService reads;
  @Autowired EntityManagerFactory emf;
  @Autowired javax.sql.DataSource runtime;
  @MockitoBean FirebaseTokenVerifier verifier;

  @Value("${local.server.port}")
  int port;

  TrailerFixtures f;
  UUID tenant, user, membership, trailer, a, b, location, known, driver, tractor;

  @BeforeEach
  void seed() throws Exception {
    f = new TrailerFixtures(flyway);
    tenant = f.db.tenant();
    user = f.db.user();
    membership = f.db.membership(tenant, user, "ACTIVE", "TENANT_ADMIN");
    a = f.customer(tenant);
    b = f.customer(tenant);
    f.db.execute("update customers set name='Visible Customer' where id=?", a);
    f.db.execute("update customers set name='Hidden Customer' where id=?", b);
    trailer = f.trailer(tenant, " tr-123 ", a);
    location = f.location(tenant);
    known = f.location(tenant);
    f.db.execute("update operational_locations set name='Current Dock' where id=?", location);
    f.db.execute("update operational_locations set name='Last Yard' where id=?", known);
    driver = f.db.driver(tenant, user);
    tractor = f.db.tractor(tenant, "TRACTOR-A");
    f.db.execute("update drivers set name='Last observed Driver' where id=?", driver);
    f.db.execute(
        "update trailers set"
            + " location_state='AT_LOCATION',current_location_id=?,last_known_location_id=?,last_seen_at=?,last_driver_id=?"
            + " where id=?",
        location,
        known,
        Timestamp.from(TIME),
        driver,
        trailer);
    when(verifier.verify(TOKEN))
        .thenReturn(new VerifiedFirebaseIdentity("fixture-" + user, null, null));
  }

  AuthenticatedUserPrincipal actor() {
    return new AuthenticatedUserPrincipal(user, "fixture-" + user, null, null);
  }

  void role(String role) throws Exception {
    f.db.execute("delete from role_assignments where membership_id=?", membership);
    f.role(tenant, membership, role, role.equals("CUSTOMER_DISPATCHER") ? a : null);
  }

  UUID fact(String type, Instant occurred, Instant recorded, String metadata) throws Exception {
    UUID id = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " trailer_movement_events(id,tenant_id,trailer_id,event_type,actor_type,source_type,actor_user_id,occurred_at,recorded_at,reason,metadata,location_id,driver_id,tractor_id,client_operation_id)"
            + " values(?,?,?,?,'HUMAN_USER','USER_ACTION',?,?,?,'Administrative reason referring to"
            + " Hidden Customer',cast(? as jsonb),?,?,?,?)",
        id,
        tenant,
        trailer,
        type,
        user,
        Timestamp.from(occurred),
        Timestamp.from(recorded),
        metadata,
        location,
        driver,
        tractor,
        UUID.randomUUID());
    return id;
  }

  UUID coupling(UUID d, UUID tr, Instant hook, boolean closed, UUID trip) throws Exception {
    UUID event = fact("HOOKED", hook, hook, "{}"), id = UUID.randomUUID();
    UUID drop = closed ? fact("DROPPED", hook.plusSeconds(1), hook.plusSeconds(1), "{}") : null;
    f.db.execute(
        "insert into"
            + " trailer_couplings(id,tenant_id,trailer_id,tractor_id,driver_id,trip_id,hooked_at,dropped_at,hook_event_id,drop_event_id,created_at)"
            + " values(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        tenant,
        trailer,
        tr,
        d,
        trip,
        Timestamp.from(hook),
        closed ? Timestamp.from(hook.plusSeconds(1)) : null,
        event,
        drop,
        Timestamp.from(hook));
    return id;
  }

  UUID order(UUID customer, String number) throws Exception {
    UUID id = UUID.randomUUID();
    f.db.execute(
        "insert into"
            + " orders(id,tenant_id,customer_id,order_number,status,created_by_user_id,created_at,updated_at)"
            + " values(?,?,?,?,'OPEN',?,now(),now())",
        id,
        tenant,
        customer,
        number,
        user);
    return id;
  }

  UUID manifest(String status, UUID... orders) throws Exception {
    UUID id = UUID.randomUUID();
    f.db.execute(
        "insert into load_manifests(id,tenant_id,trailer_id,status,created_at,updated_at,closed_at)"
            + " values(?,?,?,?,now(),now(),?)",
        id,
        tenant,
        trailer,
        status,
        status.equals("CLOSED") ? Timestamp.from(Instant.now()) : null);
    for (UUID order : orders)
      f.db.execute(
          "insert into manifest_orders(id,tenant_id,manifest_id,order_id,created_at)"
              + " values(?,?,?,?,now())",
          UUID.randomUUID(),
          tenant,
          id,
          order);
    return id;
  }

  @ParameterizedTest
  @ValueSource(strings = {"TR-123", "tr-123", "  TR-123  "})
  void normalizedExactSearchPreservesDisplay(String number) throws Exception {
    var rows =
        items(get("?operationalNumber=" + URLEncoder.encode(number, StandardCharsets.UTF_8)));
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst())
        .containsEntry("id", trailer.toString())
        .containsEntry("operationalNumber", " tr-123 ");
  }

  @Test
  void exactSearchNoSubstringAndTenantScope() throws Exception {
    assertThat(items(get("?operationalNumber=TR"))).isEmpty();
    var foreign = f.db.tenant();
    f.trailer(foreign, " tr-123 ", null);
    assertThat(items(get("?operationalNumber=TR-123"))).hasSize(1);
    missing(get("/" + f.trailer(foreign, "FOREIGN", null)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"DRIVER", "YARD_SPOTTER"})
  void genericOperationalReadsNeverGrantDriverOrYardBrowse(String role) throws Exception {
    role(role);
    problem(get(""), 403, "CAPABILITY_DENIED");
    problem(get("/" + trailer), 403, "CAPABILITY_DENIED");
    problem(get("/" + trailer + "/timeline"), 403, "CAPABILITY_DENIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"TENANT_ADMIN", "TRANSPORT_OPERATIONS", "CUSTOMER_DISPATCHER"})
  void authorizedReadersReceiveFreshEnrichedProjection(String r) throws Exception {
    role(r);
    var response = get("/" + trailer);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    var t = body(response);
    assertThat(t)
        .containsEntry("designatedCustomerName", "Visible Customer")
        .containsEntry("currentLocationName", "Current Dock")
        .containsEntry("lastKnownLocationName", "Last Yard")
        .containsEntry("lastDriverName", "Last observed Driver")
        .containsEntry("couplingState", "UNCOUPLED");
    assertThat(response.body())
        .doesNotContain("tenantId", "normalizedNumber", "actorUserId", "clientOperationId");
    assertThat(t.get("currentLocation")).isInstanceOf(Map.class);
    assertThat(t.get("lastKnownLocation")).isInstanceOf(Map.class);
  }

  @Test
  void coupledFilterUsesActualPhysicalCouplingNotPlanning() throws Exception {
    assertThat(items(get("?coupled=true"))).isEmpty();
    assertThat(items(get("?coupled=false"))).hasSize(1);
    coupling(driver, tractor, TIME, false, null);
    assertThat(items(get("?coupled=true"))).hasSize(1);
    assertThat(items(get("?coupled=false"))).isEmpty();
    assertThat(body(get("/" + trailer)))
        .containsEntry("currentDriverName", "Last observed Driver")
        .containsEntry("currentTractorOperationalNumber", "TRACTOR-A");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "coupled=yes",
        "unexpected=true",
        "operationalNumber=%09",
        "active=1",
        "coupled=TRUE"
      })
  void strictSearchRejectsInvalidOrUnknownFilters(String q) throws Exception {
    problem(get("?" + q), 400, "VALIDATION_FAILED");
  }

  @Test
  void filtersAndScopeRemainConjunctive() throws Exception {
    f.db.execute(
        "update trailers set"
            + " serviceability_state='OUT_OF_SERVICE',location_state='LOCATION_UNKNOWN',current_location_id=null"
            + " where id=?",
        trailer);
    assertThat(
            items(
                get(
                    "?serviceabilityState=OUT_OF_SERVICE&locationState=LOCATION_UNKNOWN&coupled=false")))
        .hasSize(1);
    assertThat(items(get("?serviceabilityState=IN_SERVICE&locationState=LOCATION_UNKNOWN")))
        .isEmpty();
    role("CUSTOMER_DISPATCHER");
    assertThat(items(get(""))).hasSize(1);
    f.trailer(tenant, "UNASSIGNED", null);
    UUID hidden = f.trailer(tenant, "HIDDEN", b);
    assertThat(items(get(""))).hasSize(1);
    missing(get("/" + hidden));
    missing(get("/" + hidden + "/timeline"));
    problem(get("?designatedCustomerId=" + b), 403, "CUSTOMER_SCOPE_DENIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"AT_LOCATION", "IN_TRANSIT", "LOCATION_UNKNOWN"})
  void currentAndLastKnownStayDistinct(String state) throws Exception {
    f.db.execute(
        "update trailers set location_state=?,current_location_id=? where id=?",
        state,
        state.equals("AT_LOCATION") ? location : null,
        trailer);
    var t = body(get("/" + trailer));
    assertThat(t)
        .containsEntry("locationState", state)
        .containsEntry("lastKnownLocationName", "Last Yard");
    assertThat(t.get("currentLocation"))
        .isEqualTo(
            state.equals("AT_LOCATION")
                ? Map.of(
                    "id",
                    location.toString(),
                    "name",
                    "Current Dock",
                    "type",
                    "YARD",
                    "siteId",
                    ((UUID)
                            f.db.value(
                                "select site_id from operational_locations where id=?", location))
                        .toString(),
                    "siteName",
                    "Fixture")
                : null);
  }

  @Test
  void couplingHistorySelectsLatestAndLastDriverIsAuthoritative() throws Exception {
    UUID trip = UUID.randomUUID();
    f.db.execute(
        "insert into trips(id,tenant_id,status,created_at,updated_at)"
            + " values(?,?,'PLANNED',now(),now())",
        trip,
        tenant);
    coupling(driver, tractor, TIME, true, trip);
    UUID otherDriver = f.db.driver(tenant, null), otherTractor = f.db.tractor(tenant, "TRACTOR-B");
    f.db.execute("update drivers set name='Coupled Driver' where id=?", otherDriver);
    UUID latest = coupling(otherDriver, otherTractor, TIME.plusSeconds(2), false, trip);
    var t = body(get("/" + trailer));
    assertThat(t)
        .containsEntry("currentDriverId", otherDriver.toString())
        .containsEntry("lastDriverId", driver.toString())
        .containsEntry("lastTractorId", otherTractor.toString());
    assertThat((Map<String, Object>) t.get("currentCoupling"))
        .containsEntry("id", latest.toString())
        .containsEntry("tripId", trip.toString());
    assertThat(t.get("currentCoupling")).isEqualTo(t.get("lastCoupling"));
    UUID drop = fact("DROPPED", TIME.plusSeconds(3), TIME.plusSeconds(3), "{}");
    f.db.execute(
        "update trailer_couplings set dropped_at=?,drop_event_id=? where id=?",
        Timestamp.from(TIME.plusSeconds(3)),
        drop,
        latest);
    t = body(get("/" + trailer));
    assertThat(t)
        .containsEntry("currentCoupling", null)
        .containsEntry("currentDriverId", null)
        .containsEntry("lastTractorId", otherTractor.toString());
    assertThat((Map<String, Object>) t.get("lastCoupling")).containsEntry("id", latest.toString());
  }

  @Test
  void mixedCustomerManifestFiltersChildrenAndOmitsInvisibleParents() throws Exception {
    UUID own = order(a, "VISIBLE-ORDER"), hidden = order(b, "SECRET-ORDER");
    manifest("OPEN", own, hidden);
    UUID invisible = manifest("OPEN", hidden);
    manifest("CLOSED", own);
    assertThat(JsonPath.<List<?>>read(get("/" + trailer).body(), "$.openManifests")).hasSize(2);
    role("CUSTOMER_DISPATCHER");
    var r = get("/" + trailer);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.openManifests")).hasSize(1);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.openManifests[0].orders")).hasSize(1);
    assertThat(r.body())
        .contains("VISIBLE-ORDER")
        .doesNotContain(
            "SECRET-ORDER",
            "Hidden Customer",
            b.toString(),
            hidden.toString(),
            invisible.toString(),
            "hasRestrictedOrders",
            "allocationCount");
    assertThat(body(r)).containsEntry("manifestSummaryTruncated", false);
    assertThat(JsonPath.<Boolean>read(r.body(), "$.openManifests[0].ordersTruncated")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"TENANT_ADMIN", "CUSTOMER_DISPATCHER"})
  void twentyManifestsWithSixtyVisibleOrdersDistinguishLimitedPreviewFromEmpty(String role)
      throws Exception {
    for (int i = 0; i < 20; i++) {
      var orders = new UUID[3];
      for (int j = 0; j < 3; j++) orders[j] = order(a, "LIMIT-" + i + "-" + j);
      manifest("OPEN", orders);
    }
    role(role);
    var response = get("/" + trailer);
    assertThat(response.statusCode()).isEqualTo(200);
    var previews = JsonPath.<List<Map<String, Object>>>read(response.body(), "$.openManifests");
    assertThat(previews).hasSize(role.equals("TENANT_ADMIN") ? 20 : 17);
    assertThat(JsonPath.<List<?>>read(response.body(), "$.openManifests[*].orders[*]")).hasSize(50);
    assertThat(body(response))
        .containsEntry("manifestSummaryTruncated", role.equals("TENANT_ADMIN"));
    int limited = 0;
    for (var preview : previews) {
      var orders = (List<?>) preview.get("orders");
      boolean truncated = (Boolean) preview.get("ordersTruncated");
      assertThat(truncated).isEqualTo(orders.size() < 3);
      if (truncated) limited++;
      // Every parent has real allocations. Empty previews must never claim otherwise.
      if (orders.isEmpty()) assertThat(truncated).isTrue();
    }
    assertThat(limited).isEqualTo(role.equals("TENANT_ADMIN") ? 4 : 1);
    assertThat(response.body())
        .doesNotContain("allocationCount", "hasRestrictedOrders", b.toString());
  }

  @Test
  void genuineEmptyManifestAndHiddenOrdersCannotProduceVisibleTruncation() throws Exception {
    UUID visibleManifest = manifest("OPEN", order(a, "ONE-VISIBLE"));
    UUID emptyManifest = manifest("OPEN");
    var wide = get("/" + trailer);
    var empty =
        JsonPath.<List<Map<String, Object>>>read(wide.body(), "$.openManifests").stream()
            .filter(m -> m.get("id").equals(emptyManifest.toString()))
            .findFirst()
            .orElseThrow();
    assertThat((List<?>) empty.get("orders")).isEmpty();
    assertThat(empty).containsEntry("ordersTruncated", false);
    role("CUSTOMER_DISPATCHER");
    var before = body(get("/" + trailer));
    for (int i = 0; i < 60; i++)
      f.db.execute(
          "insert into manifest_orders(id,tenant_id,manifest_id,order_id,created_at)"
              + " values(?,?,?,?,now())",
          UUID.randomUUID(),
          tenant,
          visibleManifest,
          order(b, "HIDDEN-" + i));
    var after = get("/" + trailer);
    assertThat(body(after)).isEqualTo(before);
    assertThat(after.body()).doesNotContain("HIDDEN-", b.toString(), emptyManifest.toString());
    assertThat(JsonPath.<Boolean>read(after.body(), "$.openManifests[0].ordersTruncated"))
        .isFalse();
  }

  @Test
  void detailBoundsManifestsAndTotalOrdersWithoutHiddenCounts() throws Exception {
    for (int i = 0; i < 22; i++) {
      var orders = new UUID[3];
      for (int j = 0; j < 3; j++) orders[j] = order(a, "O-" + i + "-" + j);
      manifest("OPEN", orders);
    }
    var r = get("/" + trailer);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.openManifests")).hasSize(20);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.openManifests[*].orders[*]")).hasSize(50);
    assertThat(body(r)).containsEntry("manifestSummaryTruncated", true);
    role("CUSTOMER_DISPATCHER");
    r = get("/" + trailer);
    assertThat(JsonPath.<List<?>>read(r.body(), "$.openManifests[*].orders[*]")).hasSize(50);
    assertThat(body(r)).containsEntry("manifestSummaryTruncated", false);
  }

  @Test
  void timelineDefaultLimitAndKeysetPagesUsePhysicalOccurrenceAndUuidTies() throws Exception {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 55; i++) ids.add(fact("ARRIVED", TIME, TIME.plusSeconds(i), "{}"));
    ids.sort(Comparator.comparing(UUID::toString).reversed());
    var first = get("/" + trailer + "/timeline");
    assertThat(items(first)).hasSize(50);
    assertThat(items(first))
        .extracting(i -> i.get("id"))
        .containsExactlyElementsOf(ids.subList(0, 50).stream().map(UUID::toString).toList());
    String cursor = (String) body(first).get("nextCursor");
    var second = get("/" + trailer + "/timeline?cursor=" + cursor);
    assertThat(items(second))
        .extracting(i -> i.get("id"))
        .containsExactlyElementsOf(ids.subList(50, 55).stream().map(UUID::toString).toList());
    assertThat(body(second)).containsEntry("nextCursor", null);
    assertThat(items(get("/" + trailer + "/timeline?limit=1"))).hasSize(1);
  }

  @Test
  void lateOfflineRecordedFactRemainsAtItsPhysicalOccurrencePosition() throws Exception {
    UUID recent = fact("ARRIVED", TIME.plusSeconds(10), TIME.plusSeconds(10), "{}");
    UUID late = fact("HOOKED", TIME.minusSeconds(10), TIME.plusSeconds(1000), "{}");
    assertThat(items(get("/" + trailer + "/timeline")))
        .extracting(i -> i.get("id"))
        .containsExactly(recent.toString(), late.toString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"limit=201", "limit=0", "cursor=bad", "offset=1", "page=1", "sort=recordedAt"})
  void timelineStrictParsing(String query) throws Exception {
    problem(get("/" + trailer + "/timeline?" + query), 400, "VALIDATION_FAILED");
  }

  @Test
  void everyTimelinePageReauthorizesDesignationAndMembership() throws Exception {
    fact("ARRIVED", TIME, TIME, "{}");
    fact("MARKED_OOS", TIME.plusSeconds(1), TIME.plusSeconds(1), "{}");
    role("CUSTOMER_DISPATCHER");
    String cursor = (String) body(get("/" + trailer + "/timeline?limit=1")).get("nextCursor");
    f.db.execute("update trailers set designated_customer_id=? where id=?", b, trailer);
    missing(get("/" + trailer + "/timeline?cursor=" + cursor));
    missing(get("/" + trailer));
    f.db.execute("update trailers set designated_customer_id=? where id=?", a, trailer);
    f.db.execute("update tenant_memberships set status='SUSPENDED' where id=?", membership);
    problem(get("/" + trailer + "/timeline?cursor=" + cursor), 403, "TENANT_ACCESS_DENIED");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "HOOKED",
        "DROPPED",
        "DEPARTED",
        "ARRIVED",
        "YARD_MOVED",
        "LOCATION_CORRECTED",
        "LOCATION_MARKED_UNKNOWN",
        "LOCATION_RECOVERED",
        "LOAD_STATE_CHANGED",
        "MARKED_OOS",
        "RETURNED_TO_SERVICE",
        "DESIGNATED_CUSTOMER_CHANGED"
      })
  void timelineProjectsOnlyWhitelistedFactsAndScopedReasonIsRedacted(String type) throws Exception {
    fact(
        type,
        TIME,
        TIME,
        "{\"previousLoadState\":\"EMPTY\",\"newLoadState\":\"LOADED\",\"previousDesignatedCustomerId\":\""
            + b
            + "\",\"secret\":\"hidden metadata\"}");
    var wide = get("/" + trailer + "/timeline");
    assertThat(items(wide).getFirst())
        .containsEntry("reason", "Administrative reason referring to Hidden Customer");
    role("CUSTOMER_DISPATCHER");
    var scoped = get("/" + trailer + "/timeline");
    assertThat(items(scoped).getFirst())
        .containsEntry("reason", null)
        .containsEntry("eventType", type);
    assertThat(scoped.body())
        .doesNotContain(
            "tenantId",
            "actorUserId",
            "clientOperationId",
            "metadata",
            "Hidden Customer",
            b.toString(),
            "hidden metadata");
    assertThat((Map<String, Object>) items(scoped).getFirst().get("changes"))
        .containsOnlyKeys("previousLoadState", "newLoadState");
  }

  @Test
  void foreignCustomerLocationAndTripReferencesAreNotDisclosedToScopedReader() throws Exception {
    f.db.execute("update operational_locations set customer_id=? where id=?", b, location);
    fact("ARRIVED", TIME, TIME, "{\"newLocationId\":\"" + location + "\"}");
    role("CUSTOMER_DISPATCHER");
    var detail = get("/" + trailer);
    assertThat(detail.body()).doesNotContain(location.toString(), "Current Dock");
    var page = get("/" + trailer + "/timeline");
    assertThat(page.body()).doesNotContain(location.toString(), "Current Dock");
  }

  @Test
  void fixedReadQueryCountDoesNotGrowWithTrailerOrHistoryCount() throws Exception {
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    reads.list(actor(), tenant, Map.of());
    long one = statistics.getPrepareStatementCount();
    for (int i = 0; i < 30; i++) f.trailer(tenant, "READ-" + i, a);
    statistics.clear();
    assertThat(reads.list(actor(), tenant, Map.of()).items()).hasSize(31);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(one).isLessThan(8);
    statistics.clear();
    reads.detail(actor(), tenant, trailer);
    long empty = statistics.getPrepareStatementCount();
    for (int i = 0; i < 5; i++) manifest("OPEN", order(a, "PERF-" + i));
    statistics.clear();
    reads.detail(actor(), tenant, trailer);
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(empty + 1).isLessThan(10);
    for (int i = 0; i < 55; i++) fact("ARRIVED", TIME.plusSeconds(i), TIME.plusSeconds(i), "{}");
    statistics.clear();
    reads.timeline(actor(), tenant, trailer, Map.of("limit", "1"));
    long small = statistics.getPrepareStatementCount();
    statistics.clear();
    reads.timeline(actor(), tenant, trailer, Map.of("limit", "50"));
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(small).isLessThan(8);
    statistics.clear();
    reads.list(actor(), tenant, Map.of("operationalNumber", "TR-123"));
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(one);
  }

  @Test
  void v17ContainsOnlyTwoIndexesAndRuntimeSecurityIsUnchanged() throws Exception {
    flyway.validate();
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
    String sql =
        new String(
            getClass()
                .getResourceAsStream("/db/migration/V17__trailer_read_projection_indexes.sql")
                .readAllBytes(),
            StandardCharsets.UTF_8);
    assertThat(sql.split(";")).hasSize(3);
    assertThat(sql.toLowerCase())
        .doesNotContain("grant", "alter", "create table", "create policy", "create function");
    assertThat(
            f.db.value(
                "select count(*) from pg_indexes where indexname in"
                    + " ('trailer_couplings_trailer_history','load_manifests_trailer_lookup')"))
        .isEqualTo(2L);
    try (var c = runtime.getConnection();
        var s = c.createStatement()) {
      assertThat(c.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
      try (var r =
          s.executeQuery(
              "select rolsuper,rolbypassrls,has_schema_privilege(current_user,'public','CREATE')"
                  + " from pg_roles where rolname=current_user")) {
        r.next();
        assertThat(r.getBoolean(1)).isFalse();
        assertThat(r.getBoolean(2)).isFalse();
        assertThat(r.getBoolean(3)).isFalse();
      }
      for (String table :
          List.of("trailers", "trailer_couplings", "trailer_movement_events", "load_manifests")) {
        try (var r = s.executeQuery("select count(*) from " + table)) {
          r.next();
          assertThat(r.getLong(1)).isZero();
        }
        try (var r =
            s.executeQuery(
                "select relrowsecurity,relowner=(select oid from pg_roles where"
                    + " rolname=current_user) from pg_class where oid='"
                    + table
                    + "'::regclass")) {
          r.next();
          assertThat(r.getBoolean(1)).isTrue();
          assertThat(r.getBoolean(2)).isFalse();
        }
      }
      c.setAutoCommit(false);
      s.execute("select set_config('app.current_user_id','" + user + "',true)");
      for (String table :
          List.of("trailers", "trailer_couplings", "trailer_movement_events", "load_manifests")) {
        try (var r = s.executeQuery("select count(*) from " + table)) {
          r.next();
          assertThat(r.getLong(1)).isZero();
        }
      }
      c.rollback();
    }
  }

  @Test
  void queryPlansHaveIndexBackedLookupPathsWithoutExactCostAssertions() throws Exception {
    // Empty relations can legitimately tie with unrelated indexes plus a sort.
    // Exercise the ordered access paths on representative history and current data.
    f.db.execute(
        "insert into trailer_movement_events(id,tenant_id,trailer_id,event_type,actor_type,"
            + "source_type,actor_user_id,occurred_at,recorded_at) select gen_random_uuid(),?,?,"
            + "'ARRIVED','HUMAN_USER','USER_ACTION',?,?::timestamptz+g*interval '1 second',"
            + "?::timestamptz+g*interval '1 second' from generate_series(1,1000) g",
        tenant,
        trailer,
        user,
        Timestamp.from(TIME),
        Timestamp.from(TIME));
    for (int i = 0; i < 40; i++) coupling(driver, tractor, TIME.plusSeconds(i * 2L), true, null);
    coupling(driver, tractor, TIME.plusSeconds(100), false, null);
    f.db.execute(
        "insert into load_manifests(id,tenant_id,trailer_id,status,created_at,updated_at)"
            + " select gen_random_uuid(),?,?,'OPEN',now(),now()+g*interval '1 second'"
            + " from generate_series(1,100) g",
        tenant,
        trailer);
    for (String table :
        List.of("trailers", "trailer_couplings", "trailer_movement_events", "load_manifests"))
      f.db.execute("analyze " + table);
    try (var c = runtime.getConnection();
        var s = c.createStatement()) {
      c.setAutoCommit(false);
      s.execute("select set_config('app.current_tenant_id','" + tenant + "',true)");
      var cases =
          Map.of(
              "operational_number_normalized",
                  "select id from trailers where tenant_id='"
                      + tenant
                      + "' and operational_number_normalized='TR-123'",
              "trailer_couplings_active_trailer",
                  "select id from trailer_couplings where tenant_id='"
                      + tenant
                      + "' and trailer_id='"
                      + trailer
                      + "' and dropped_at is null",
              "trailer_couplings_trailer_history",
                  "select id from trailer_couplings where tenant_id='"
                      + tenant
                      + "' and trailer_id='"
                      + trailer
                      + "' order by hooked_at desc,id desc limit 1",
              "load_manifests_trailer_lookup",
                  "select id from load_manifests where tenant_id='"
                      + tenant
                      + "' and trailer_id='"
                      + trailer
                      + "' order by updated_at desc,id desc limit 20",
              "trailer_movements_history",
                  "select id from trailer_movement_events where tenant_id='"
                      + tenant
                      + "' and trailer_id='"
                      + trailer
                      + "' order by occurred_at desc,id desc limit 50");
      // Observe real default-planner execution first, without requiring a particular node/cost.
      for (var entry : cases.entrySet()) {
        String plan;
        try (var result =
            s.executeQuery("explain (analyze, buffers, format json) " + entry.getValue())) {
          assertThat(result.next()).isTrue();
          plan = result.getString(1);
        }
        int expectedRows =
            entry.getKey().equals("trailer_movements_history")
                ? 50
                : entry.getKey().equals("load_manifests_trailer_lookup") ? 20 : 1;
        assertThat(JsonPath.<Number>read(plan, "$[0].Plan['Actual Rows']").doubleValue())
            .isEqualTo(expectedRows);
        System.out.println(
            "Default PostgreSQL read plan: "
                + entry.getKey()
                + "; nodes="
                + JsonPath.read(plan, "$..['Node Type']")
                + "; indexes="
                + JsonPath.read(plan, "$..['Index Name']")
                + "; returnedRows="
                + expectedRows);
      }
      // Separately verify available ordered index paths, independent of planner cost estimates.
      // These method settings are transaction-local test diagnostics, never production settings.
      s.execute("set local enable_seqscan=off");
      s.execute("set local enable_bitmapscan=off");
      s.execute("set local enable_sort=off");
      for (var entry : cases.entrySet()) {
        var plan = new StringBuilder();
        try (var r = s.executeQuery("explain " + entry.getValue())) {
          while (r.next()) plan.append(r.getString(1));
        }
        assertThat(plan.toString()).contains("Index");
        if (entry.getKey().equals("operational_number_normalized"))
          assertThat(plan.toString()).contains("operational_number_normalized");
        else assertThat(plan.toString()).contains(entry.getKey());
      }
      c.rollback();
    }
  }

  HttpResponse<String> get(String suffix) throws Exception {
    try (var c = HttpClient.newHttpClient()) {
      return c.send(
          HttpRequest.newBuilder(
                  URI.create(
                      "http://127.0.0.1:"
                          + port
                          + "/api/v1/tenants/"
                          + tenant
                          + "/trailers"
                          + suffix))
              .header("Authorization", "Bearer " + TOKEN)
              .header("X-Request-Id", "trailer-read-test")
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  Map<String, Object> body(HttpResponse<String> r) {
    return JsonPath.read(r.body(), "$");
  }

  List<Map<String, Object>> items(HttpResponse<String> r) {
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    return JsonPath.read(r.body(), "$.items");
  }

  void missing(HttpResponse<String> r) {
    problem(r, 404, "RESOURCE_NOT_FOUND");
    assertThat(body(r)).containsEntry("detail", "The resource was not found.");
  }

  void problem(HttpResponse<String> r, int status, String code) {
    assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
    assertThat(body(r)).containsEntry("code", code).containsKey("traceId");
    assertThat(r.body()).doesNotContain(TOKEN, "constraint", "SQL");
  }
}
