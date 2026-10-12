package com.trailersaas.documents;

import com.trailersaas.identity.AuthenticatedUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Synchronous, server-side evidence rules. Every call joins an existing Tenant transaction. */
public interface OperationalEvidencePolicy {
  enum Mode { SIGNED_BOL, NO_RECEIVER_AVAILABLE }
  record PreTripPhotos(UUID front, UUID rear, UUID driverSide, UUID passengerSide) {}
  record Delivery(Mode mode, UUID bolDocumentId, UUID deliveryPhotoDocumentId, String comment) {}
  record Receipt(UUID id, UUID stopId, Mode mode, String comment, Instant completedAt) {}

  void validatePreTrip(AuthenticatedUserPrincipal actor, UUID tenant, UUID trip, UUID tractor, PreTripPhotos photos);
  /**
   * Own, attachment-free upload set for a NEW inspection. #43 must first authorize its
   * real Shift/Tractor context, then create the inspection and attachments atomically.
   * This validates files only; it is never approval of an inspection or departure.
   */
  void validatePreTripUploads(AuthenticatedUserPrincipal actor, UUID tenant, PreTripPhotos photos);
  void validateDelivery(AuthenticatedUserPrincipal actor, UUID tenant, UUID stop, Delivery delivery);
  /** Fresh full resource/document authority before the future #45 ledger, including committed replay. */
  void validateDeliveryForReplay(AuthenticatedUserPrincipal actor, UUID tenant, UUID stop, Delivery delivery);
  void validateDriverDamage(AuthenticatedUserPrincipal actor, UUID tenant, UUID trip, UUID stop, List<UUID> photos);
  void validateTrailerOos(AuthenticatedUserPrincipal actor, UUID tenant, UUID trailer, List<UUID> photos);
  void validateFuel(AuthenticatedUserPrincipal actor, UUID tenant, UUID trip, UUID tractor, UUID receipt);
  /** Own, attachment-free receipt; #45 separately authorizes a Shift-only Fuel context. */
  void validateFuelUpload(AuthenticatedUserPrincipal actor, UUID tenant, UUID receipt);

  /**
   * Validates first, invokes the owning workflow, verifies its persisted COMPLETED Stop/event,
   * then inserts immutable evidence in the SAME transaction. The caller owns idempotency,
   * service obligations, Order/Trip effects and the authoritative completion action.
   * Trips never depends on Documents; higher-level application orchestration calls both APIs.
   */
  Receipt recordDelivery(AuthenticatedUserPrincipal actor, UUID tenant, UUID stop, Delivery delivery, Runnable completeStop);
}
