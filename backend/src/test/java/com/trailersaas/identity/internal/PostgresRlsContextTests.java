package com.trailersaas.identity.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.trailersaas.identity.AuthenticatedUserPrincipal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresRlsContextTests {
    final EntityManager manager = mock(EntityManager.class);
    final Session session = mock(Session.class);
    final Connection connection = mock(Connection.class);
    final PreparedStatement read = mock(PreparedStatement.class);
    final PreparedStatement write = mock(PreparedStatement.class);
    final ResultSet settings = mock(ResultSet.class);
    final PostgresRlsContext context = new PostgresRlsContext(manager);
    final UUID tenant = UUID.randomUUID();
    final UUID user = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(manager.isJoinedToTransaction()).thenReturn(true);
        when(manager.unwrap(Session.class)).thenReturn(session);
        doAnswer(call -> { call.<Work>getArgument(0).execute(connection); return null; })
                .when(session).doWork(any(Work.class));
        when(connection.prepareStatement(contains("current_setting"))).thenReturn(read);
        when(connection.prepareStatement(contains("set_config"))).thenReturn(write);
        when(read.executeQuery()).thenReturn(settings);
        when(write.executeQuery()).thenReturn(mock(ResultSet.class));
        when(settings.next()).thenReturn(true);
    }

    @AfterEach
    void clearTransactionMetadata() { TransactionSynchronizationManager.clear(); }

    @Test
    void usesPreparedTransactionLocalParametersOnJpaConnection() throws Exception {
        context.establishTenant(tenant);
        verify(write).setString(1, tenant.toString());
        verify(write).setString(2, "");
        verify(connection).prepareStatement(contains("set_config('app.current_tenant_id', ?, true)"));
        verify(connection).prepareStatement(contains("set_config('app.current_user_id', ?, true)"));
    }

    @Test
    void bootstrapUsesOnlyAuthenticatedInternalUser() throws Exception {
        context.establishBootstrapUser(principal());
        verify(write).setString(1, "");
        verify(write).setString(2, user.toString());
    }

    @ParameterizedTest
    @CsvSource({"false,true,false", "true,false,false", "true,true,true"})
    void requiresActiveJoinedNonAutocommitTransaction(boolean active, boolean joined, boolean autoCommit)
            throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(active);
        when(manager.isJoinedToTransaction()).thenReturn(joined);
        when(connection.getAutoCommit()).thenReturn(autoCommit);
        assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(tenant));
        verify(write, never()).executeQuery();
    }

    @Test
    void repeatedSameTenantIsAllowedButSwitchOrBootstrapIsRejectedWithoutChangingSettings() throws Exception {
        when(settings.getString(1)).thenReturn(tenant.toString());
        context.establishTenant(tenant);
        clearInvocations(write);
        assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(UUID.randomUUID()))
                .withMessage("RLS context cannot change within a transaction");
        assertThatIllegalStateException().isThrownBy(() -> context.establishBootstrapUser(principal()));
        verify(write, never()).executeQuery();
    }

    @Test
    void repeatedSameBootstrapIsAllowedButUserSwitchOrTenantIsRejected() throws Exception {
        when(settings.getString(2)).thenReturn(user.toString());
        context.establishBootstrapUser(principal());
        clearInvocations(write);
        assertThatIllegalStateException().isThrownBy(() -> context.establishBootstrapUser(
                new AuthenticatedUserPrincipal(UUID.randomUUID(), "uid", null, null)));
        assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(tenant));
        verify(write, never()).executeQuery();
    }

    @Test
    void bothSettingsAlreadyPresentFailClosed() throws Exception {
        when(settings.getString(1)).thenReturn(tenant.toString());
        when(settings.getString(2)).thenReturn(user.toString());
        assertThatIllegalStateException().isThrownBy(() -> context.establishTenant(tenant));
        assertThatIllegalStateException().isThrownBy(() -> context.establishBootstrapUser(principal()));
        verify(write, never()).executeQuery();
    }

    @Test
    void rejectsMissingIdentityAndTenantBeforeTouchingDatabase() {
        assertThatNullPointerException().isThrownBy(() -> context.establishTenant(null));
        assertThatNullPointerException().isThrownBy(() -> context.establishBootstrapUser(null));
        assertThatNullPointerException().isThrownBy(() -> context.establishBootstrapUser(
                new AuthenticatedUserPrincipal(null, "uid", null, null)));
        verifyNoInteractions(session);
    }

    private AuthenticatedUserPrincipal principal() {
        return new AuthenticatedUserPrincipal(user, "uid", null, null);
    }
}
