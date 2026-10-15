package com.trailersaas.notifications;

import java.util.*;

/** Explicit compiled, approved policy only. No default production implementation in #56 (OQ-059).
 * Called in a Tenant transaction at creation AND again at delivery. Return current eligible candidates.
 * The infrastructure additionally verifies fresh membership/capability/related-resource scope. */
public interface AlertRecipientPolicy {
 String code();
 Set<UUID> recipients(Context alert);
 record Context(UUID tenantId,UUID alertId,UUID trailerId,UUID designatedCustomerId,String alertType){}
}
