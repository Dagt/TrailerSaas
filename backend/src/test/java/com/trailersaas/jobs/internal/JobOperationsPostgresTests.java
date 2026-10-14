package com.trailersaas.jobs.internal;

import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jmx.enabled=true", "spring.jmx.unique-names=true",
        "management.endpoints.jmx.exposure.include=metrics", "management.endpoints.web.exposure.include=health"})
class JobOperationsPostgresTests extends JobsFixture {
    @Autowired JobMetrics actualMetrics;
    @Autowired MeterRegistry actualRegistry;
    @Value("${local.server.port}") int port;

    @Test void actualActuatorLocalJmxExposesTechnicalMetricsWithoutOpeningPublicHttp() throws Exception {
        var id=enqueue("permanent",5);
        var worker=new DurableJobWorker(store,registry,settings(List.of(tenant)),actualMetrics); workers.add(worker);
        worker.pollOnce();
        assertThat(status(id)).isEqualTo("DEAD");
        assertThat(actualRegistry.get("jobs.stored").tag("status","dead").gauge().value()).isEqualTo(1);
        var server=ManagementFactory.getPlatformMBeanServer();
        var names=server.queryNames(new ObjectName("org.springframework.boot:type=Endpoint,name=Metrics,*"),null);
        assertThat(names).isNotEmpty();
        boolean observed=false;
        for(var name:names) for(var operation:server.getMBeanInfo(name).getOperations()) {
            if(operation.getSignature().length==0) {
                var value=server.invoke(name,operation.getName(),new Object[0],new String[0]);
                if(value!=null && value.toString().contains("jobs.stored")) observed=true;
            }
        }
        assertThat(observed).isTrue();
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/metrics")).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain("jobs.stored",tenant.toString());
        }
    }
}
