package com.trailersaas.identity.internal;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class InvitationContractsTests {
    @Test void emailNormalizationPreservesAliasesAndUsesStableLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(InvitationContracts.normalizeEmail("  Maria.Example+Ops@Example.COM  ")).isEqualTo("maria.example+ops@example.com");
            assertThat(InvitationContracts.normalizeEmail("USER@EXAMPLE.COM")).isEqualTo("user@example.com");
            assertThat(InvitationContracts.normalizeEmail("I@example.com")).isEqualTo("i@example.com");
            assertThat(InvitationContracts.normalizeEmail("user+one@example.com")).isNotEqualTo(InvitationContracts.normalizeEmail("user+two@example.com"));
            assertThat(InvitationContracts.normalizeEmail("first.last@example.com")).isNotEqualTo(InvitationContracts.normalizeEmail("firstlast@example.com"));
        } finally { Locale.setDefault(previous); }
    }
    @Test void jdkTokensAreCanonicalRandom256BitsAndHashIsStable() {
        var tokens = new InvitationToken();
        var generated = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String token = tokens.generate();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            assertThat(tokens.validatedHash(token)).isEqualTo(InvitationToken.hash(token)).matches("[0-9a-f]{64}");
            assertThat(generated.add(token)).isTrue();
        }
        assertThat(InvitationToken.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
    @ParameterizedTest @ValueSource(strings = {"synthetic-secret-invitation-token", "", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
    void invalidOrNoncanonicalTokenNeverLeaksIntoException(String token) {
        assertThatThrownBy(() -> new InvitationToken().validatedHash(token)).isInstanceOf(InvitationFailure.class)
                .hasMessage("The request is invalid.").hasNoCause();
    }
    @Test void requestAndCreateResponseToStringRedactRawToken() {
        var token = "synthetic-secret-invitation-token";
        assertThat(new InvitationContracts.AcceptRequest(token).toString()).doesNotContain(token);
        assertThat(new InvitationContracts.Created(UUID.randomUUID(), "email", null, InvitationStatus.PENDING,
                java.time.Instant.now(), List.of(), token).toString()).doesNotContain(token);
    }
    @Test void strictJsonAcceptanceRejectsClientIdentityTenantAndRoleHints() {
        var mapper = JsonMapper.builder().build();
        var token = new InvitationToken().generate();
        assertThat(mapper.readValue("{\"token\":\"" + token + "\"}", InvitationContracts.AcceptRequest.class).token()).isEqualTo(token);
        for (var key : Set.of("tenantId", "email", "roles", "customerId", "userId")) {
            assertThatThrownBy(() -> mapper.readValue("{\"token\":\"" + token + "\",\"" + key + "\":\"bad\"}",
                    InvitationContracts.AcceptRequest.class)).isInstanceOf(RuntimeException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings = {
            "{\"roleCode\":\"DRIVER\",\"scopeType\":\"CUSTOMER\",\"customerId\":\"00000000-0000-0000-0000-000000000001\"}",
            "{\"roleCode\":\"CUSTOMER_DISPATCHER\",\"scopeType\":\"TENANT\"}",
            "{\"roleCode\":\"CUSTOMER_DISPATCHER\",\"scopeType\":\"CUSTOMER\"}",
            "{\"roleCode\":\"UNKNOWN\",\"scopeType\":\"TENANT\"}",
            "{\"roleCode\":\"DRIVER\",\"scopeType\":\"LOCATION\"}"})
    void invalidRoleScopeMatrixRejected(String role) {
        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("{\"email\":\"user@example.com\",\"roles\":[" + role + "]}",
                InvitationContracts.CreateRequest.class)).isInstanceOf(RuntimeException.class);
    }
    @Test void rejectsEmptyDuplicateAndUnknownFieldsWhileAllowingDifferentCustomers() {
        var role = Map.of("roleCode", "DRIVER", "scopeType", "TENANT");
        for (var roles : List.of(List.of(), List.of(role, role))) {
            assertThatThrownBy(() -> InvitationContracts.CreateRequest.parse(Map.of("email", "user@example.com", "roles", roles)))
                    .isInstanceOf(InvitationFailure.class);
        }
        assertThatThrownBy(() -> InvitationContracts.CreateRequest.parse(Map.of("email", "user@example.com", "roles", List.of(role), "expiresAt", "tomorrow")))
                .isInstanceOf(InvitationFailure.class);
        var a = Map.of("roleCode", "CUSTOMER_DISPATCHER", "scopeType", "CUSTOMER", "customerId", UUID.randomUUID().toString());
        var b = Map.of("roleCode", "CUSTOMER_DISPATCHER", "scopeType", "CUSTOMER", "customerId", UUID.randomUUID().toString());
        assertThat(InvitationContracts.CreateRequest.parse(Map.of("email", "user@example.com", "roles", List.of(a, b))).roles()).hasSize(2);
    }
}
