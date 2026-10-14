package com.trailersaas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBoundariesTests {

    private final ApplicationModules modules = ApplicationModules.of(TrailerSaasApplication.class);

    @Test
    void discoversAllScaffoldModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("identity", "customers", "drivers", "tractors",
                        "trailers", "orders", "trips", "yard", "inspections", "documents",
                        "notifications", "audit", "jobs");
    }

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
