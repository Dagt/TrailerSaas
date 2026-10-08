package com.trailersaas;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CorsConfigurationTests {
    @Test void explicitOriginsAndLocalOnlyLoopback() {
        assertThat(CorsConfiguration.origins("https://web.example.test,https://other.example.test", false)).hasSize(2);
        assertThat(CorsConfiguration.origins("http://localhost:5173,http://127.0.0.1:5173", true)).hasSize(2);
        assertThat(CorsConfiguration.origins("", false)).isEmpty();
    }
    @Test void rejectsWildcardPathsCredentialsHttpRemoteAndNonlocalLoopbackWithoutEchoingValues() {
        for (String value : new String[]{"*", "https://*.example.test", "http://web.example.test", "https://localhost:5173",
                "http://127.0.0.1:5173", "https://web.example.test/path", "https://secret:password@web.example.test", "https://web.example.test?x=secret"}) {
            assertThatThrownBy(() -> CorsConfiguration.origins(value, false)).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(value).hasMessageNotContaining("password");
        }
    }
}
