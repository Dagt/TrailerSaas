package com.trailersaas.notifications.internal;

import java.util.*;

interface FcmTransport {
 enum Outcome { ACCEPTED_BY_FCM, RETRYABLE_FAILURE, PERMANENT_FAILURE, INVALIDATED_TOKEN }
 record Result(Outcome outcome,String code){}
 /** Runtime-only tokens: never log/serialize targets or persist them in jobs/receipts. */
 List<Result> send(List<String> tokens,Map<String,String> references);
}
