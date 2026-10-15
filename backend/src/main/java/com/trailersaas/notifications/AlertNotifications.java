package com.trailersaas.notifications;

import java.util.*;

/** Internal transaction-joining port. Caller selects a registered APPROVED server policy,
 * never client-supplied recipient IDs. No policy is approved/registered by default. */
public interface AlertNotifications { List<UUID> requestDelivery(UUID tenantId,UUID alertId,String policyCode); }
