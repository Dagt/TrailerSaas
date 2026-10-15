package com.trailersaas.identity;

import java.util.Optional;
import java.util.UUID;

/** Server-side recipient eligibility only; never authenticates a caller or grants write authority. */
public interface NotificationRecipientAccess { Optional<CustomerAccessScope> scope(UUID tenantId,UUID userId); }
