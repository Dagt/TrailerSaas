package com.trailersaas;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;

/** Allowlisted diagnostic facts, never exception messages, SQL, parameters or a Throwable log argument. */
final class SafeInfrastructureDiagnostics {
    private static final Logger LOG=LoggerFactory.getLogger(SafeInfrastructureDiagnostics.class);
    private SafeInfrastructureDiagnostics() {}

    static void record(Exception failure) {
        var facts=facts(failure);
        var event=LOG.atError().addKeyValue("failureCategory",facts.category())
                .addKeyValue("databaseFailure",facts.databaseFailure())
                .addKeyValue("exceptionType",facts.exceptionType())
                .addKeyValue("rootCauseType",facts.rootCauseType());
        if(facts.sqlState()!=null) { event.addKeyValue("sqlState",facts.sqlState()); }
        // The request filter supplies traceId/requestId and an already-redacted http.path via MDC.
        event.log("Infrastructure request failed");
    }

    static Facts facts(Throwable failure) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var causes=new ArrayDeque<Throwable>();
        causes.add(failure);
        String sqlState=null,root=failure.getClass().getName();
        boolean database=false,connection=false,timeout=false,transaction=false;
        while(!causes.isEmpty()) {
            var cause=causes.removeFirst();
            if(!seen.add(cause)) { continue; }
            root=cause.getClass().getName();
            database|=cause instanceof SQLException || cause instanceof DataAccessException;
            connection|=cause instanceof DataAccessResourceFailureException;
            timeout|=cause instanceof SQLTimeoutException || cause instanceof QueryTimeoutException;
            transaction|=cause instanceof TransactionException || cause instanceof org.hibernate.TransactionException;
            if(cause instanceof SQLException sql) {
                var state=sql.getSQLState();
                if(sqlState==null && state!=null && state.matches("[A-Z0-9]{5}")) { sqlState=state; }
                if(sql.getNextException()!=null) { causes.addLast(sql.getNextException()); }
            }
            if(cause.getCause()!=null) { causes.addFirst(cause.getCause()); }
            // Inspect the entire original cause chain before a secondary rollback failure.
            if(cause instanceof TransactionSystemException tx && tx.getApplicationException()!=null) {
                causes.addFirst(tx.getApplicationException());
            }
        }
        String category;
        if((sqlState!=null && sqlState.startsWith("08")) || connection) { category="db_connection_failure"; }
        else if(timeout || "57014".equals(sqlState) || "HYT00".equals(sqlState) || "HYT01".equals(sqlState)) { category="db_timeout"; }
        else if(sqlState!=null && sqlState.startsWith("23")) { category="integrity_failure"; }
        else if(transaction) { category="transaction_failure"; }
        else { category="unexpected_failure"; }
        return new Facts(category,database,failure.getClass().getName(),root,sqlState);
    }

    record Facts(String category,boolean databaseFailure,String exceptionType,String rootCauseType,String sqlState) {}
}
