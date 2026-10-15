package com.trailersaas.notifications;

/** Explicit approved compiled opt-in for new STALE occurrences only.
 * No production implementation while OQ-059 is deferred. The normal recipient
 * port still verifies every candidate; this marker grants no visibility. */
public interface StaleAlertRecipientPolicy extends AlertRecipientPolicy {}
