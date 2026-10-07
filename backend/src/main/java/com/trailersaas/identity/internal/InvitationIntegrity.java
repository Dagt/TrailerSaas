package com.trailersaas.identity.internal;

import java.sql.SQLException;

/** Only expected UNIQUE/FK/CHECK rejections from invitation writes become safe domain conflicts. */
final class InvitationIntegrity {
    private InvitationIntegrity() {}

    static boolean isExpectedRejection(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return "23505".equals(sql.getSQLState()) || "23503".equals(sql.getSQLState())
                        || "23514".equals(sql.getSQLState());
            }
        }
        // Missing/unknown SQLState is not evidence of an expected domain rejection.
        return false;
    }
}
