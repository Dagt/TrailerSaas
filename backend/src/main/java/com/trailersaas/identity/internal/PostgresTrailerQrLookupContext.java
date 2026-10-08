package com.trailersaas.identity.internal;

import java.util.Base64;
import jakarta.persistence.EntityManager;
import com.trailersaas.identity.TrailerQrLookupContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class PostgresTrailerQrLookupContext implements TrailerQrLookupContext {
    private final EntityManager manager;
    PostgresTrailerQrLookupContext(EntityManager manager) { this.manager=manager; }
    @Override public void establish(String token) {
        if(!canonical(token)) { throw new IllegalArgumentException("Invalid QR lookup context."); }
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !manager.isJoinedToTransaction()) {
            throw new IllegalStateException("QR lookup requires an active database transaction.");
        }
        manager.unwrap(Session.class).doWork(connection->{
            if(connection.getAutoCommit()) { throw new IllegalStateException("QR lookup requires a non-autocommit connection."); }
            try(var query=connection.prepareStatement("""
                    SELECT NULLIF(current_setting('app.current_tenant_id',true),''),
                           NULLIF(current_setting('app.current_user_id',true),''),
                           NULLIF(current_setting('app.current_invitation_hash',true),''),
                           NULLIF(current_setting('app.current_trailer_qr_token',true),'')
                    """)) {
                try(var values=query.executeQuery()) {
                    values.next();
                    if(values.getString(1)!=null || values.getString(2)!=null || values.getString(3)!=null
                            || (values.getString(4)!=null && !token.equals(values.getString(4)))) {
                        throw new IllegalStateException("QR lookup context cannot change or mix modes.");
                    }
                }
            }
            try(var query=connection.prepareStatement("SELECT set_config('app.current_trailer_qr_token',?,true)")) {
                query.setString(1,token); query.executeQuery().close();
            }
        });
    }
    private boolean canonical(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{22}")) { return false; }
        var bytes=Base64.getUrlDecoder().decode(token);
        return bytes.length==16 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token);
    }
}
