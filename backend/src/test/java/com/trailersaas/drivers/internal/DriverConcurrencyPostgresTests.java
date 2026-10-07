package com.trailersaas.drivers.internal;

import com.trailersaas.EquipmentFixtures;
import com.trailersaas.PostgresIntegrationTest;
import com.trailersaas.identity.TenantTransactionExecutor;
import java.util.UUID;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DriverConcurrencyPostgresTests extends PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired TenantTransactionExecutor transactions;
    @Autowired DriverRepository repository;

    @Test void simultaneousUpdatesCannotSilentlyOverwriteEachOther() throws Exception {
        var fixtures=new EquipmentFixtures(flyway);
        UUID tenant=fixtures.tenant();
        UUID id=fixtures.driver(tenant,null);
        var loaded=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var attempts=new java.util.ArrayList<Future<Throwable>>();
            for(int i=0;i<2;i++) {
                int attempt=i;
                attempts.add(pool.submit(()-> {
                    try {
                        transactions.execute(tenant,()-> {
                            var entity=repository.resource(tenant,id,false);
                            entity.requireVersion(0);
                            loaded.countDown();
                            try { if(!loaded.await(20,TimeUnit.SECONDS)) { throw new AssertionError("Both transactions must load version zero"); } }
                            catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                            entity.name="changed-"+attempt;
                            repository.flush();
                            return null;
                        });
                        return null;
                    } catch(RuntimeException error) { return error; }
                }));
            }
            var outcomes=new java.util.ArrayList<Throwable>();
            for(var attempt:attempts) { outcomes.add(attempt.get(30,TimeUnit.SECONDS)); }
            assertThat(outcomes.stream().filter(e->e==null).count()).isEqualTo(1);
            var failure=outcomes.stream().filter(e->e!=null).findFirst().orElseThrow();
            boolean optimistic=false;
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
                optimistic|=cause instanceof jakarta.persistence.OptimisticLockException
                    || cause instanceof org.springframework.dao.OptimisticLockingFailureException
                    || cause instanceof org.hibernate.StaleStateException;
            }
            assertThat(optimistic).as("The second database update must fail optimistic concurrency").isTrue();
        }
        assertThat(fixtures.value("SELECT version FROM drivers WHERE id=?",id)).isEqualTo(1L);
    }
}

