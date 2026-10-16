package com.trailersaas.audit.internal;

import com.trailersaas.*;
import com.trailersaas.audit.*;
import com.trailersaas.identity.*;
import static com.trailersaas.audit.AuditWriter.*;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP producers/readers plus the real mandatory writer and limited PostgreSQL runtime role. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired AuditWriter writer;
    @Autowired TenantContext context;
    @Autowired TenantTransactionExecutor transactions;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource runtime;
    @MockitoBean FirebaseTokenVerifier verifier;
    @Value("${local.server.port}") int port;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=HttpClient.newHttpClient();
    EquipmentFixtures f; UUID tenant,user,member,customer;
    @BeforeEach void seed() throws Exception {
        f=new EquipmentFixtures(flyway);tenant=f.tenant();user=f.user();member=f.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
        f.execute("update tenants set status='ACTIVE' where id=?",tenant);
        f.execute("insert into tenant_settings values(?,72,null,now(),now(),0)",tenant);
        customer=UUID.randomUUID();f.execute("insert into customers values(?,?,'Original','ACTIVE',now(),now(),0)",customer,tenant);
        when(verifier.verify("audit-test")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+user,null,null));
    }
    @Test void settingsCapturesRealBeforeAfterActorMembershipAndServerTime() throws Exception {
        var before=Instant.now().minusSeconds(1);
        ok(call("PATCH","/settings","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":80}"),200);
        var e=events().getFirst();assertThat(e.get("actionCode")).isEqualTo("TENANT_SETTINGS_UPDATED");
        assertThat(map(e.get("actor"))).containsEntry("type","HUMAN_USER").containsEntry("userId",user.toString()).containsEntry("membershipId",member.toString());
        assertThat(e).containsEntry("sourceType","USER_ACTION").containsEntry("reason",null).containsEntry("entityId",tenant.toString());
        assertThat(map(e.get("beforeData"))).containsEntry("staleTrailerThresholdHours",72).containsEntry("version",0);
        assertThat(map(e.get("afterData"))).containsEntry("staleTrailerThresholdHours",80).containsEntry("version",1);
        assertThat(Instant.parse((String)e.get("occurredAt"))).isAfter(before).isBefore(Instant.now().plusSeconds(1));
    }
    @Test void normalizedNoopValidationStaleRetryAndDeniedMutationDoNotInventSuccess()throws Exception {
        ok(call("PATCH","/settings","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":72}"),200);assertThat(count()).isZero();
        ok(call("PATCH","/settings","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":0}"),400);assertThat(count()).isZero();
        ok(call("PATCH","/settings","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":90}"),200);
        ok(call("PATCH","/settings","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":90}"),409);assertThat(count()).isEqualTo(1);
        f.execute("delete from role_assignments where membership_id=?",member);
        ok(call("PATCH","/settings","{\"expectedVersion\":1,\"staleTrailerThresholdHours\":99}"),403);assertThat(count()).isEqualTo(1);
    }
    @Test void customersCreateEditAndNoopAreAuditedAtActualDomainVersions()throws Exception {
        var c=body(ok(call("POST","/customers","{\"name\":\"New\"}"),201));var id=(String)c.get("id");
        ok(call("PATCH","/customers/"+id,"{\"expectedVersion\":0,\"name\":\"Changed\"}"),200);
        ok(call("PATCH","/customers/"+id,"{\"expectedVersion\":1,\"name\":\"Changed\"}"),200);
        var e=events();assertThat(e).extracting(i->i.get("actionCode")).containsExactly("CUSTOMER_UPDATED","CUSTOMER_CREATED");
        assertThat(e.getLast().get("beforeData")).isNull();assertThat(map(e.getFirst().get("beforeData"))).containsEntry("name","New");
        assertThat(map(e.getFirst().get("afterData"))).containsEntry("name","Changed").containsEntry("version",1);
    }
    @Test void sitesLocationsAndAssociationOccurrencesHaveExplicitMinimalSnapshots()throws Exception {
        var site=body(ok(call("POST","/sites","{\"name\":\"Yard\",\"timezone\":\"UTC\"}"),201));var sid=(String)site.get("id");
        ok(call("PATCH","/sites/"+sid,"{\"expectedVersion\":0,\"name\":\"Main Yard\"}"),200);
        ok(call("POST","/customers/"+customer+"/sites","{\"siteId\":\""+sid+"\"}"),201);
        var location=body(ok(call("POST","/sites/"+sid+"/locations","{\"name\":\"Parking\",\"type\":\"YARD\"}"),201));
        ok(call("PATCH","/locations/"+location.get("id"),"{\"expectedVersion\":0,\"name\":\"Parking East\"}"),200);
        ok(call("DELETE","/customers/"+customer+"/sites/"+sid,null),204);
        ok(call("POST","/customers/"+customer+"/sites","{\"siteId\":\""+sid+"\"}"),201);
        assertThat(events()).extracting(i->i.get("actionCode")).contains("SITE_CREATED","SITE_UPDATED","LOCATION_CREATED","LOCATION_UPDATED","CUSTOMER_SITE_DISASSOCIATED");
        assertThat(events().stream().filter(e->e.get("actionCode").equals("CUSTOMER_SITE_ASSOCIATED")).count()).isEqualTo(2);
    }
    @Test void driverAndTractorEditsAndAccountLinkDoNotSerializePrivateCertificates()throws Exception {
        var driver=body(ok(call("POST","/drivers","{\"name\":\"Business driver\",\"licenseNumber\":\"private-license-synthetic\",\"medicalCertificateExpires\":\"2000-01-01\"}"),201));
        var id=driver.get("id");
        ok(call("PATCH","/drivers/"+id,"{\"expectedVersion\":0,\"licenseNumber\":\"other-private-license\"}"),200);
        var target=f.user();f.membership(tenant,target,"ACTIVE","DRIVER");
        ok(call("POST","/drivers/"+id+"/user-link","{\"expectedVersion\":1,\"userId\":\""+target+"\"}"),200);
        ok(call("DELETE","/drivers/"+id+"/user-link","{\"expectedVersion\":2}"),204);
        var tractor=body(ok(call("POST","/tractors","{\"operationalNumber\":\"Unit 1\"}"),201));
        ok(call("PATCH","/tractors/"+tractor.get("id"),"{\"expectedVersion\":0,\"active\":false}"),200);
        assertThat(events()).extracting(e->e.get("actionCode")).contains("DRIVER_CREATED","DRIVER_UPDATED","DRIVER_USER_LINKED","DRIVER_USER_UNLINKED","TRACTOR_CREATED","TRACTOR_UPDATED");
        var raw=call("GET","/audit-events",null).body();assertThat(raw).contains("licenseNumberPresent","redactedChanges").doesNotContain("private-license","2000-01-01");
    }
    @Test void trailerMetadataAndQrLifecycleAreAuditedWithoutOpaqueTokensOrNotes()throws Exception {
        var trailer=body(ok(call("POST","/trailers","{\"operationalNumber\":\"Audit unit\",\"designatedCustomerId\":\""+customer+"\",\"capabilityNotes\":\"private-notes-synthetic\"}"),201));
        String path="/trailers/"+trailer.get("id");
        ok(call("PATCH",path,"{\"expectedVersion\":0,\"capabilityNotes\":\"changed-private-notes\"}"),200);
        var qr=body(ok(call("POST",path+"/qr-identities","{}"),201));
        ok(call("POST",path+"/qr-identities/"+qr.get("id")+"/revocation","{}"),204);
        ok(call("POST",path+"/qr-identities/"+qr.get("id")+"/revocation","{}"),204);
        assertThat(events()).extracting(e->e.get("actionCode")).containsExactly("TRAILER_QR_REVOKED","TRAILER_QR_ISSUED","TRAILER_UPDATED","TRAILER_CREATED");
        assertThat(call("GET","/audit-events",null).body()).doesNotContain((String)qr.get("publicToken"),"private-notes");
        assertThat(f.value("select count(*) from trailer_movement_events where tenant_id=?",tenant)).isEqualTo(0L);
    }
    @Test void physicalCorrectionRetainsItsSpecializedHistoryWithoutDuplicateAdministrativeAudit()throws Exception {
        var tf=new TrailerFixtures(flyway);var id=tf.trailer(tenant,"Physical audit boundary",customer);var location=tf.location(tenant);
        ok(call("POST","/trailers/"+id+"/location-corrections","{\"expectedVersion\":0,\"newLocationId\":\""+location+"\",\"reason\":\"Actual observed location\"}"),200);
        assertThat(f.value("select count(*) from trailer_movement_events where tenant_id=?",tenant)).isEqualTo(1L);
        assertThat(count()).isZero();
    }
    @Test void timestampTiesUseIdTieBreakerAndCrossTenantCursorIsRejected()throws Exception {
        transactions.execute(tenant,()->{
            for(int i=0;i<4;i++)jdbc.update("insert into audit_events(id,tenant_id,actor_type,source_type,action_code,entity_type,entity_id,before_data,after_data,occurred_at) values(?,?,'SYSTEM','SYSTEM_PROCESS','CUSTOMER_UPDATED','CUSTOMER',?,?::jsonb,?::jsonb,'2026-01-01T00:00:00Z')",UUID.randomUUID(),tenant,customer,json.writeValueAsString(state(i)),json.writeValueAsString(state(i+1)));
            return null;
        });
        var page=body(ok(call("GET","/audit-events?limit=2",null),200));var cursor=(String)page.get("nextCursor");
        var next=body(ok(call("GET","/audit-events?limit=2&cursor="+cursor,null),200));
        var ids=new HashSet<Object>();items(page).forEach(e->assertThat(ids.add(e.get("id"))).isTrue());items(next).forEach(e->assertThat(ids.add(e.get("id"))).isTrue());assertThat(ids).hasSize(4);assertThat(next.get("nextCursor")).isNull();
        tenant=f.tenant();f.membership(tenant,user,"ACTIVE","TENANT_ADMIN");f.execute("update tenants set status='ACTIVE' where id=?",tenant);
        ok(call("GET","/audit-events?cursor="+cursor,null),400);
    }
    @Test void invitationsRevokeAndSystemExpiryExcludeEmailNameAndAcceptanceSecret()throws Exception {
        var request="{\"email\":\"audit-private@example.test\",\"displayName\":\"Private invitee\",\"roles\":[{\"roleCode\":\"DRIVER\",\"scopeType\":\"TENANT\"}]}";
        var first=body(ok(call("POST","/invitations",request),201));
        ok(call("POST","/invitations/"+first.get("id")+"/revoke",null),200);
        ok(call("POST","/invitations/"+first.get("id")+"/revoke",null),200);
        var expired=body(ok(call("POST","/invitations",request),201));
        f.execute("update membership_invitations set expires_at=now()-interval '1 day' where id=?",UUID.fromString((String)expired.get("id")));
        ok(call("POST","/invitations/"+expired.get("id")+"/revoke",null),200);
        assertThat(events()).hasSize(4);var expiry=events().getFirst();assertThat(expiry).containsEntry("actionCode","INVITATION_EXPIRED").containsEntry("sourceType","SYSTEM_PROCESS");
        assertThat(map(expiry.get("actor"))).containsEntry("type","SYSTEM").containsEntry("userId",null);
        assertThat(call("GET","/audit-events",null).body()).doesNotContain("audit-private@example.test","Private invitee",(String)first.get("acceptanceToken"),(String)expired.get("acceptanceToken"));
    }
    @Test void acceptingInvitationCreatesMembershipAndAuditAtomicallyAndAckRetryDoesNotDuplicate()throws Exception {
        var invitation=body(ok(call("POST","/invitations","{\"email\":\"new-actor@example.test\",\"roles\":[{\"roleCode\":\"DRIVER\",\"scopeType\":\"TENANT\"}]}"),201));
        var invitee=f.user();when(verifier.verify("audit-invitee")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+invitee,"new-actor@example.test",null,true));
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/invitations/accept")).header("Authorization","Bearer audit-invitee").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\""+invitation.get("acceptanceToken")+"\"}")).build();
        var first=http.send(b,HttpResponse.BodyHandlers.ofString());ok(first,200);
        var retry=http.send(b,HttpResponse.BodyHandlers.ofString());ok(retry,200);assertThat(retry.body()).isEqualTo(first.body());
        assertThat(events()).hasSize(3);var materialized=events().stream().filter(e->e.get("actionCode").equals("MEMBERSHIP_CREATED")).findFirst().orElseThrow();
        assertThat(map(materialized.get("actor"))).containsEntry("userId",invitee.toString()).containsEntry("membershipId",body(first).get("membershipId"));
    }
    @Test void writerRequiresExistingTransactionAndPinnedMatchingTenant() {
        assertThatThrownBy(()->write(Actor.human(user),0,1,null)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(s->{write(Actor.human(user),0,1,null);return null;})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->transactions.execute(UUID.randomUUID(),()->{write(Actor.human(user),0,1,null);return null;})).isInstanceOf(IllegalStateException.class);
    }
    @Test void systemAndIntegrationHaveNoInventedHumanAndReasonIsActual()throws Exception {
        transactions.execute(tenant,()->{write(Actor.system(),0,1,"Verified configuration repair");write(Actor.integration(),1,2,null);return null;});
        var e=events();assertThat(e).hasSize(2);
        assertThat(map(e.getFirst().get("actor"))).containsEntry("type","INTEGRATION").containsEntry("userId",null).containsEntry("membershipId",null);
        assertThat(e.getLast()).containsEntry("sourceType","SYSTEM_PROCESS").containsEntry("reason","Verified configuration repair");
    }
    @Test void domainAndAuditCommitAndRollbackTogether()throws Exception {
        transactions.execute(tenant,()->{jdbc.update("update customers set name='Committed',version=1 where id=?",customer);write(Actor.human(user),0,1,null);return null;});
        assertThat(count()).isEqualTo(1);assertThat(f.value("select name from customers where id=?",customer)).isEqualTo("Committed");
        assertThatThrownBy(()->transactions.execute(tenant,()->{jdbc.update("update customers set name='Rolled back',version=2 where id=?",customer);write(Actor.human(user),1,2,null);throw new IllegalStateException("induced business failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isEqualTo(1);assertThat(f.value("select name from customers where id=?",customer)).isEqualTo("Committed");
    }
    @Test void databaseAuditFailureRollsBackActualAdministrativeHttpMutation()throws Exception {
        // Migration-owner test fault, scoped to this fixture; production does not gain this privilege.
        f.execute("create function audit_test_reject() returns trigger language plpgsql as $$ begin if new.tenant_id='"+tenant+"'::uuid then raise exception 'induced audit failure'; end if; return new; end $$");
        f.execute("create trigger audit_test_reject before insert on audit_events for each row execute function audit_test_reject()");
        try {
            var r=call("PATCH","/settings","{\"expectedVersion\":0,\"staleTrailerThresholdHours\":99}");
            ok(r,500);assertThat(r.body()).doesNotContain("induced audit failure","audit_test_reject","SQL");
            assertThat(f.value("select stale_trailer_threshold_hours from tenant_settings where tenant_id=?",tenant)).isEqualTo(72);assertThat(count()).isZero();
        }finally{f.execute("drop trigger audit_test_reject on audit_events");f.execute("drop function audit_test_reject()");}
    }
    @Test void sameCommittedChangeIsNotDuplicatedAndDivergentReplayFailsClosed()throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);return null;});
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);return null;});assertThat(count()).isEqualTo(1);
        assertThatThrownBy(()->transactions.execute(tenant,()->{write(Actor.system(),0,1,null);return null;})).isInstanceOf(IllegalStateException.class);assertThat(count()).isEqualTo(1);
    }
    @Test void concurrentDomainUpdatesHaveOneWinnerAndOneAuditRecord()throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->{start.await();return call("PATCH","/customers/"+customer,"{\"expectedVersion\":0,\"name\":\"First\"}");});
            var b=pool.submit(()->{start.await();return call("PATCH","/customers/"+customer,"{\"expectedVersion\":0,\"name\":\"Second\"}");});
            start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS).statusCode(),b.get(20,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        }assertThat(count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"CUSTOMER_DISPATCHER","DRIVER","YARD_SPOTTER","TRANSPORT_OPERATIONS"})
    void noNewHumanGrantsOrCustomerScopedAudit(String role)throws Exception {
        f.execute("delete from role_assignments where membership_id=?",member);
        f.membership(tenant,f.user(),"ACTIVE",role); // Another user's role cannot authorize this principal.
        f.execute("insert into role_assignments values(?,?,?,?,?,?,now())",UUID.randomUUID(),tenant,member,role,role.equals("CUSTOMER_DISPATCHER")?"CUSTOMER":"TENANT",role.equals("CUSTOMER_DISPATCHER")?customer:null);
        ok(call("GET","/audit-events",null),403);ok(call("GET","/audit-events/"+UUID.randomUUID(),null),403);
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDED","DISABLED","revoked","tenant-disabled"})
    void accessRevocationIsFreshEvenWithOldCursor(String condition)throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);write(Actor.human(user),1,2,null);return null;});
        var page=body(ok(call("GET","/audit-events?limit=1",null),200));
        if(condition.equals("revoked"))f.execute("delete from role_assignments where membership_id=?",member);
        else if(condition.equals("tenant-disabled"))f.execute("update tenants set status='DISABLED' where id=?",tenant);
        else f.execute("update tenant_memberships set status=? where id=?",condition,member);
        ok(call("GET","/audit-events?cursor="+page.get("nextCursor"),null),403);
        assertThat(f.value("select actor_membership_id from audit_events where tenant_id=? limit 1",tenant)).isEqualTo(member);
        assertThat(f.value("select actor_user_id from audit_events where tenant_id=? limit 1",tenant)).isEqualTo(user);
    }
    @Test void historicalActorStillReadableAfterMembershipDisabledByAnotherAuthorizedAdmin()throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);return null;});
        f.execute("update tenant_memberships set status='DISABLED' where id=?",member);
        var reader=f.user();f.membership(tenant,reader,"ACTIVE","TENANT_ADMIN");
        when(verifier.verify("audit-test")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+reader,null,null));
        assertThat(map(events().getFirst().get("actor"))).containsEntry("userId",user.toString()).containsEntry("membershipId",member.toString());
        assertThatThrownBy(()->f.execute("delete from users where id=?",user)).isInstanceOf(java.sql.SQLException.class);
    }
    @Test void listKeysetFiltersTiesAndDetailUseStableOriginalData()throws Exception {
        transactions.execute(tenant,()->{for(int i=1;i<=5;i++)write(Actor.human(user),i-1,i,null);return null;});
        String filters="&actorUserId="+user+"&entityType=CUSTOMER&entityId="+customer+"&actionCode=CUSTOMER_UPDATED&from=2000-01-01T00:00:00Z&to=2099-01-01T00:00:00Z";
        var seen=new HashSet<Object>();String cursor=null;int pages=0;
        do {
            var page=body(ok(call("GET","/audit-events?limit=2"+filters+(cursor==null?"":"&cursor="+cursor),null),200));
            for(var e:items(page)){assertThat(seen.add(e.get("id"))).isTrue();ok(call("GET","/audit-events/"+e.get("id"),null),200);}
            cursor=(String)page.get("nextCursor");pages++;
        }while(cursor!=null);
        assertThat(pages).isEqualTo(3);assertThat(seen).hasSize(5);
        assertThat(items(body(ok(call("GET","/audit-events?actorUserId="+UUID.randomUUID(),null),200)))).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"limit=0","limit=101","limit=01","limit=x","limit=1&limit=2","unknown=x","actorUserId=bad","actionCode=DROP","entityType=USER","entityId=bad","from=bad","from=%2B999999-01-01T00:00:00Z","to=-9999-01-01T00:00:00Z","from=2030-01-01T00:00:00Z&to=2000-01-01T00:00:00Z","cursor=bad","cursor=","actorUserId="})
    void invalidFiltersAndMalformedCursorRejectSafely(String query)throws Exception {
        ok(call("GET","/audit-events?"+query,null),400);
    }
    @Test void cursorBoundToActorTenantAndFiltersDoesNotGrantAccess()throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);write(Actor.human(user),1,2,null);return null;});
        var cursor=body(ok(call("GET","/audit-events?limit=1",null),200)).get("nextCursor");
        ok(call("GET","/audit-events?cursor="+cursor+"&entityType=CUSTOMER",null),400);
        var other=f.user();f.membership(tenant,other,"ACTIVE","TENANT_ADMIN");
        when(verifier.verify("audit-test")).thenReturn(new VerifiedFirebaseIdentity("fixture-"+other,null,null));
        ok(call("GET","/audit-events?cursor="+cursor,null),400);
    }
    @Test void foreignDetailAndTenantDoNotRevealEntityOrExistence()throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);return null;});
        var id=events().getFirst().get("id");var first=tenant;
        tenant=f.tenant();f.execute("update tenants set status='ACTIVE' where id=?",tenant);f.membership(tenant,user,"ACTIVE","TENANT_ADMIN");
        assertThat(events()).isEmpty();var foreign=ok(call("GET","/audit-events/"+id,null),404);var missing=ok(call("GET","/audit-events/"+UUID.randomUUID(),null),404);
        assertThat(foreign.body()).contains("RESOURCE_NOT_FOUND").doesNotContain(first.toString(),"Original");assertThat(missing.body()).contains("RESOURCE_NOT_FOUND");
        var stranger=f.tenant();tenant=stranger;ok(call("GET","/audit-events",null),403);
    }
    @Test void unauthenticatedAndWriteHttpEndpointsAreDenied()throws Exception {
        var r=http.send(HttpRequest.newBuilder(URI.create(base()+"/audit-events")).GET().build(),HttpResponse.BodyHandlers.ofString());ok(r,401);
        ok(call("POST","/audit-events","{}"),405);ok(call("PATCH","/audit-events/"+UUID.randomUUID(),"{}"),405);ok(call("DELETE","/audit-events/"+UUID.randomUUID(),null),405);
    }
    @Test void runtimeRlsGrantsImmutabilityAndConnectionReuseAreReal()throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);return null;});var other=f.tenant();
        assertThat(jdbc.queryForObject("select count(*) from audit_events",Long.class)).isZero();
        assertThat(transactions.execute(other,()->jdbc.queryForObject("select count(*) from audit_events",Long.class))).isZero();
        assertThat(transactions.execute(tenant,()->jdbc.queryForObject("select count(*) from audit_events",Long.class))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events",Long.class)).isZero();
        for(var sql:List.of("update audit_events set reason='tampered'","delete from audit_events","truncate audit_events"))assertThatThrownBy(()->transactions.execute(tenant,()->jdbc.update(sql))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->f.execute("update audit_events set reason='tampered' where tenant_id=?",tenant)).isInstanceOf(java.sql.SQLException.class);
        assertThatThrownBy(()->f.execute("delete from audit_events where tenant_id=?",tenant)).isInstanceOf(java.sql.SQLException.class);
        assertThat(jdbc.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user",Boolean.class)).isFalse();
        try(var c=runtime.getConnection();var q=c.createStatement();var r=q.executeQuery("select count(*) from audit_events")){r.next();assertThat(r.getInt(1)).isZero();}
        assertThat(f.value("select relrowsecurity and relforcerowsecurity from pg_class where oid='audit_events'::regclass")).isEqualTo(true);
    }
    @Test void simultaneousIndependentRuntimeConnectionsAndBootstrapCannotCrossRead()throws Exception {
        transactions.execute(tenant,()->{write(Actor.human(user),0,1,null);return null;});var other=f.tenant();
        try(var a=runtime.getConnection();var b=runtime.getConnection()) {
            a.setAutoCommit(false);b.setAutoCommit(false);
            for(var entry:Map.of(a,tenant,b,other).entrySet())try(var q=entry.getKey().prepareStatement("select set_config('app.current_tenant_id',?,true)")){q.setString(1,entry.getValue().toString());q.execute();}
            try(var q=a.createStatement();var r=q.executeQuery("select count(*) from audit_events")){r.next();assertThat(r.getInt(1)).isEqualTo(1);}
            try(var q=b.createStatement();var r=q.executeQuery("select count(*) from audit_events")){r.next();assertThat(r.getInt(1)).isZero();}
            try(var q=a.prepareStatement("select set_config('app.current_user_id',?,true)")){q.setString(1,user.toString());q.execute();}
            try(var q=a.createStatement();var r=q.executeQuery("select count(*) from audit_events")){r.next();assertThat(r.getInt(1)).isZero();}
            a.rollback();b.rollback();
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_events",Long.class)).isZero();
    }
    @Test void unpinnedForeignActorForeignEntityAndInvalidProvenanceFailClosed()throws Exception {
        var foreign=f.user();f.membership(f.tenant(),foreign,"ACTIVE","TENANT_ADMIN");
        assertThatThrownBy(()->transactions.execute(tenant,()->{write(Actor.human(foreign),0,1,null);return null;})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->transactions.execute(tenant,()->{writer.record(tenant,Actor.system(),Action.CUSTOMER_UPDATED,UUID.randomUUID(),state(0),state(1),null);return null;})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->transactions.execute(tenant,()->{write(new Actor(ActorType.SYSTEM,SourceType.USER_ACTION,user),0,1,null);return null;})).isInstanceOf(IllegalArgumentException.class);assertThat(count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"foreign-tenant","foreign-membership","human-no-user","system-with-user","unknown-action","unknown-entity","unknown-key","nested-secret","nonobject","missing-version","noop","negative-version","blank-reason","oversize"})
    void databaseConstraintsRejectFabricatedHistoryEvenWithoutJavaWriter(String defect)throws Exception {
        var args=new ArrayList<Object>(Arrays.asList(UUID.randomUUID(),tenant,"HUMAN_USER",user,member,"USER_ACTION","CUSTOMER_UPDATED","CUSTOMER",customer,"{\"name\":\"old\",\"version\":0}","{\"name\":\"new\",\"version\":1}",null));
        switch(defect){
            case "foreign-tenant" -> args.set(1,f.tenant());
            case "foreign-membership" -> {var t=f.tenant();args.set(4,f.membership(t,user,"ACTIVE","TENANT_ADMIN"));}
            case "human-no-user" -> args.set(3,null);
            case "system-with-user" -> {args.set(2,"SYSTEM");args.set(5,"SYSTEM_PROCESS");}
            case "unknown-action" -> args.set(6,"REQUEST_LOGGED");
            case "unknown-entity" -> args.set(7,"USER");
            case "unknown-key" -> args.set(10,"{\"version\":1,\"token\":\"secret\"}");
            case "nested-secret" -> args.set(10,"{\"version\":1,\"name\":{\"password\":\"secret\"}}");
            case "nonobject" -> args.set(10,"[]");
            case "missing-version" -> args.set(10,"{\"name\":\"new\"}");
            case "noop" -> args.set(10,args.get(9));
            case "negative-version" -> args.set(10,"{\"version\":-1}");
            case "blank-reason" -> args.set(11,"  ");
            case "oversize" -> args.set(10,json.writeValueAsString(Map.of("version",1,"name","x".repeat(65537))));
        }
        assertThatThrownBy(()->transactions.execute(tenant,()->jdbc.update("insert into audit_events(id,tenant_id,actor_type,actor_user_id,actor_membership_id,source_type,action_code,entity_type,entity_id,before_data,after_data,reason) values(?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?)",args.toArray()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"token","password","authorization","signedUrl","firebaseToken","privateNotes"})
    void writerCannotStoreUnknownSecretFields(String field)throws Exception {
        assertThatThrownBy(()->transactions.execute(tenant,()->{writer.record(tenant,Actor.system(),Action.CUSTOMER_UPDATED,customer,state(0),Map.of("version",1,field,"synthetic-secret"),null);return null;})).isInstanceOf(IllegalArgumentException.class);assertThat(count()).isZero();
    }
    @Test void indexesSupportTenantTimeAndSelectiveQueries()throws Exception {
        assertThat(f.value("select count(*) from pg_indexes where tablename='audit_events' and indexname in ('audit_events_tenant_time','audit_events_entity_time','audit_events_actor_time','audit_events_action_time','audit_events_membership_time')")).isEqualTo(5L);
        transactions.execute(tenant,()->{
            jdbc.execute("set local enable_seqscan=off");
            String plan=String.join("\n",jdbc.queryForList("explain select * from audit_events where tenant_id=? order by occurred_at desc,id desc limit 51",String.class,tenant));
            assertThat(plan).contains("audit_events_tenant_time").doesNotContain("Sort");
            for(var clause:List.of("entity_type='CUSTOMER' and entity_id='"+customer+"'","actor_user_id='"+user+"'","action_code='CUSTOMER_UPDATED'")) {
                assertThat(String.join("\n",jdbc.queryForList("explain select * from audit_events where tenant_id=? and "+clause+" order by occurred_at desc,id desc limit 51",String.class,tenant))).contains("Index");
            }return null;
        });
    }
    void write(Actor actor,int before,int after,String reason){writer.record(tenant,actor,Action.CUSTOMER_UPDATED,customer,state(before),state(after),reason);}
    Map<String,Object> state(int version){return snapshot("name","State "+version,"status","ACTIVE","version",version);}
    long count()throws Exception{return ((Number)f.value("select count(*) from audit_events where tenant_id=?",tenant)).longValue();}
    String base(){return "http://127.0.0.1:"+port+"/api/v1/tenants/"+tenant;}
    HttpResponse<String> call(String method,String path,String data)throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base()+path)).header("Authorization","Bearer audit-test").header("Content-Type","application/json");
        return http.send(b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(data)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> ok(HttpResponse<String> r,int status){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return r;}
    @SuppressWarnings("unchecked") Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    @SuppressWarnings("unchecked") Map<String,Object> body(HttpResponse<String> r){return json.readValue(r.body(),Map.class);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> items(Map<String,Object> p){return (List<Map<String,Object>>)p.get("items");}
    List<Map<String,Object>> events()throws Exception{return items(body(ok(call("GET","/audit-events?limit=100",null),200)));}
}
